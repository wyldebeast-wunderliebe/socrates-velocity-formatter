/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * You should have received a copy of the GNU General Public License
 * (for example /usr/src/linux/COPYING); if not, write to the Free
 * Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package com.w20e.socrates.formatting;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.configuration.Configuration;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import com.w20e.socrates.data.Instance;
import com.w20e.socrates.data.Node;
import com.w20e.socrates.model.ConstraintViolation;
import com.w20e.socrates.model.InvalidPathExpression;
import com.w20e.socrates.model.ItemProperties;
import com.w20e.socrates.model.ItemPropertiesImpl;
import com.w20e.socrates.model.Model;
import com.w20e.socrates.model.NodeValidator;
import com.w20e.socrates.model.NodeValidatorWrapper;
import com.w20e.socrates.model.XRefSolver;
import com.w20e.socrates.process.RunnerContext;
import com.w20e.socrates.process.ValidationException;
import com.w20e.socrates.rendering.Control;
import com.w20e.socrates.rendering.Group;
import com.w20e.socrates.rendering.Option;
import com.w20e.socrates.rendering.RenderOptionsImpl;
import com.w20e.socrates.rendering.Renderable;
import com.w20e.socrates.rendering.TextBlock;
import com.w20e.socrates.rendering.Translatable;
import com.w20e.socrates.rendering.Vocabulary;
import com.w20e.socrates.util.FillProcessor;
import com.w20e.socrates.util.UTF8ResourceBundle;
import com.w20e.socrates.util.UTF8ResourceBundleImpl;
import com.w20e.socrates.workflow.ActionResultImpl;
import com.w20e.socrates.workflow.Failure;

/**
 * Velocity formatter for the Socrates engine. The formatter is configured with
 * a mapping from classes to templates. This formatter is implemented as a
 * singleton.
 */
public final class VelocityHTMLFormatter implements Formatter {

	/**
	 * Render debugging info or not.
	 */
	private boolean debug;

	/**
	 * Default render options.
	 */
	private Map<String, Object> renderOptions;

	/**
	 * Config.
	 */
	private Configuration cfg;

	/**
	 * Hold actual Velocity engine.
	 */
	private VelocityEngine engine;

	/**
	 * Initialize this class' logging.
	 */
	private static final Logger LOGGER = Logger
			.getLogger(VelocityHTMLFormatter.class.getName());

	private static final String TRUE = "true";
	private static final String FALSE = "false";

	/**
	 * Create a new formatter for the given rendering and configuration.
	 * 
	 * @param config
	 *            config for formatter
	 * @todo handle options
	 */
	public void init(final Configuration config) {

		this.cfg = config;

		this.renderOptions = new HashMap<String,Object>();
		final Properties props = new Properties();
		String key;
		Object value;

		for (final Iterator<?> i = this.cfg.getKeys(); i.hasNext();) {

			key = (String) i.next();

			if (key.startsWith("formatter.velocity.")) {

				value = this.cfg.getProperty(key);

				if (value instanceof List<?>) {

					String newVal = "";

					for (final Iterator<?> j = ((List<?>) value).iterator(); j
							.hasNext();) {
						newVal = newVal + j.next() + ",";
					}
					props.setProperty(key.substring(19), newVal);
					LOGGER.finest("Setting Velocity property "
							+ key.substring(19) + " to list "
							+ this.cfg.getProperty(key).toString());
				} else {
					props.setProperty(key.substring(19),
							this.cfg.getString(key));
					LOGGER.finest("Setting Velocity property "
							+ key.substring(19) + " to "
							+ this.cfg.getProperty(key).toString());
				}
			} else if (key.startsWith("formatter.options.")) {

				setRenderingProperty(key.substring(18), this.cfg.getString(key));
			}
		}

		if (TRUE.equals(config.getString("formatter.debug", FALSE))) {
			this.debug = true;
			LOGGER.log(Level.WARNING, "Using debug mode in formatting");
		}

		try {
			this.engine = new VelocityEngine();
			this.engine.setApplicationAttribute("engine", this.engine);
			this.engine.setApplicationAttribute("cfg", this.cfg);
			this.engine.init(props);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Can't configure Velocity. This is BAD!",
					e);
		}
	}

    private void setRenderingProperty(final String pName, final String pValue) {

    	if (pName.startsWith("enable_")) {
        	this.renderOptions.put(pName, Boolean.valueOf(pValue));    		
        	this.renderOptions.put(pName.substring(7), Boolean.valueOf(pValue));    		
    	} else if (pName.startsWith("disable_")) {
        	this.renderOptions.put(pName, Boolean.valueOf(pValue));
        	this.renderOptions.put(pName.substring(8), Boolean.valueOf(pValue));    		
    	} else {
    		this.renderOptions.put(pName, pValue);
    	}
    }
    
	/**
	 * Enable reset of properties after initialization.
	 * 
	 * @param property
	 * @param value
	 */
	public void setProperty(final String property, final String value) {

		this.engine.setProperty(property, value);
	}

	/**
	 * Format list of items. This formatter uses velocity and thus needs a
	 * template to work. The template to use is determined as follows: 1. if the
	 * RunnerContext contains a value for the property "template", this one is
	 * used; 2. else if the configuration given to init contains a key for
	 * "formatter.template", this one is used; 3. the template name defaults to
	 * "main.vm".
	 * 
	 * @param items
	 *            List of items to use.
	 * @param out
	 *            OutputStream to use
	 * @param pContext
	 *            Processing context
	 * @throws Exception
	 *             in case of Velocity errors, or output stream errors.
	 */
	public void format(final Collection<Renderable> items,
			final OutputStream out, final RunnerContext pContext)
			throws FormatException {

		VelocityContext context = new VelocityContext();
		try {
			Writer writer;
			writer = new OutputStreamWriter(out, this.cfg.getString(
					"formatter.encoding", "UTF-8"));

			Locale locale = pContext.getLocale();
			
			LOGGER.fine("Using locale " + locale + " with prefix "
					+ this.cfg.getString("formatter.locale.prefix"));

			UTF8ResourceBundle bundle = UTF8ResourceBundleImpl.getBundle(
					this.cfg.getString("formatter.locale.prefix"), locale);

			LOGGER.fine("Found resource locale: " + bundle.getLocale());

			LOGGER.finer("Formatting " + items.size() + " items");

			fillContext(items, context, pContext, bundle);

			this.engine.mergeTemplate((String) pContext.getProperty("template",
					this.cfg.getString("formatter.template", "main.vm")),
					this.cfg.getString("formatter.encoding", "UTF-8"), context,
					writer);
			writer.flush();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in formatting items", e);
			// Print full stack to logging;
			PrintWriter writer = new PrintWriter(new StringWriter());
			e.printStackTrace(writer);
			LOGGER.log(Level.SEVERE, writer.toString());
			throw new FormatException(e.getMessage());
		}
	}

	/**
	 * For each item in the list, even when nested, we need to add variables for
	 * required, alert, and item value. These will be made available to the
	 * velocity context in a hash of hashes.
	 * 
	 * @param items
	 *            all items to use
	 * @param context
	 *            velocity context
	 * @param pContext
	 *            processing context
	 * @param bundle
	 *            resource bundle
	 * @todo we loop over errors, but only add one to the context...
	 * @todo recursion could be a tad more efficient.
	 */
	private void fillContext(final Collection<Renderable> items,
			final VelocityContext context, final RunnerContext pContext,
			final UTF8ResourceBundle bundle) {

		// Let's declare variables outside of loop
		Renderable rItem = null;
		Model model = pContext.getModel();
		Instance inst = pContext.getInstance();
		HashMap<String, HashMap<String, Object>> values = new HashMap<String, HashMap<String, Object>>();
		List<Renderable> fItems = new ArrayList<Renderable>();

		// Let's loop over renderable items.
		//
		for (Iterator<Renderable> i = items.iterator(); i.hasNext();) {

			rItem = i.next();

			addItemToContext(rItem, context, pContext, bundle, values);

			fItems.add(rItem);
		}

		// Add meta data, both model and instance.
		context.put("metadata", model.getMetaData());
		context.put("instance_metadata", inst.getMetaData());
		context.put("instance_values", new NodeValidatorWrapper(model, inst));

		// Add i18n data
		context.put("i18n", bundle);
		context.put("locale", pContext.getLocale());
		context.put("items", fItems);
		context.put("context", values);

		context.put("hasNext",
				Boolean.valueOf(pContext.getStateManager().hasNext()));
		context.put("hasPrevious",
				Boolean.valueOf(pContext.getStateManager().hasPrevious()));

		try {
			context.put("stateId", items.iterator().next().getId());
		} catch (Exception e) {
			// in case of no items, just ignore this.
			context.put("stateId", "");
		}

		if (this.debug) {
			context.put("debug", TRUE);
		} else {
			context.put("debug", FALSE);
		}

		LOGGER.finest("Context: " + pContext);

        RenderOptionsImpl localOptions = new RenderOptionsImpl(this.renderOptions);
        
        if (pContext.getProperty("renderOptions") != null) {
        	// Add specific render options
        	LOGGER.finest("Rendering options: "
        			+ pContext.getProperty("renderOptions"));
        	
        	try {
            	Map<String, String> contextOptions = (Map<String, String>) pContext.getProperty("renderOptions");

            	for (String key: contextOptions.keySet()) {
        			if (Boolean.valueOf(contextOptions.get(key))) {
        				localOptions.enable(key);
        			} else {
        				localOptions.disable(key);        				
        			}
        		}	
        	} catch (ClassCastException cce) {
        		LOGGER.severe("Couldn't cast renderoptions to map");
        	}
        }

    	context.put("renderOptions", localOptions);

		// Any errors?
		if (ActionResultImpl.FAIL.equals(pContext.getResult().toString())) {
			context.put("errors", TRUE);
		} else {
			context.put("errors", FALSE);
		}

		LOGGER.fine("Progress: "
				+ pContext.getStateManager().getProgressPercentage());

		// add progress
		context.put("percentage_done", Integer.valueOf(pContext
				.getStateManager().getProgressPercentage()));

		LOGGER.finest("Context filled");
	}

	/**
	 * Add single item to context, or, if it's a group, add it's controls.
	 * 
	 * @param rItem
	 * @param context
	 * @param pContext
	 * @param bundle
	 */
	private void addItemToContext(final Renderable rItem,
			final VelocityContext context, final RunnerContext pContext,
			final UTF8ResourceBundle bundle,
			final Map<String, HashMap<String, Object>> values) {

		/**
		 * If it's a group, just add it's controls to the context.
		 */
		if (rItem instanceof Group) {

			addGroupToContext((Group) rItem, pContext, values);

			for (Iterator<Renderable> i = ((Group) rItem).getItems().iterator(); i
					.hasNext();) {

				addItemToContext(i.next(), context, pContext, bundle, values);
			}
			return;
		}

		HashMap<String, Object> itemCtx = new HashMap<String, Object>();
		Model model = pContext.getModel();
		Instance inst = pContext.getInstance();

		if (rItem instanceof TextBlock) {
			
			LOGGER.fine("Adding text block to context: " + rItem.getId());
			
			String text = this.translate(((TextBlock) rItem).getText(), pContext.getLocale());

			LOGGER.finest("Found translation: " + text);

			text = FillProcessor.processFills(text, inst, model,
					pContext.getRenderConfig(), pContext.getLocale());
			
			LOGGER.finest("Fills processed: " + text);

			itemCtx.put("text", text);

			values.put(rItem.getId(), itemCtx);
						
			return;
		}
		
		if (!(rItem instanceof Control)) {
			return;
		}

		Control control = (Control) rItem;
		String bind = control.getBind();
		Node node;

		try {
			node = inst.getNode(bind);
		} catch (InvalidPathExpression e1) {
			return;
		}

		ItemProperties props = model.getItemProperties(bind);

		if (props == null) {
			props = new ItemPropertiesImpl(bind);
		}

		try {
			LOGGER.fine("do we have calculations: " + props.getCalculate());

			LOGGER.fine("Raw node value: " + node.getValue());

			Object val = NodeValidator.getValue(node, props, model, inst);
			
			LOGGER.fine("Adding item "
					+ control.getId()
					+ " to context with value "
					+ val
					+ " and lexical value "
					+ control.getDisplayValue(val, props.getDatatype(),
							pContext.getLocale()));

			if (val == null) {
				itemCtx.put("value", "");
			} else {
				itemCtx.put("value", val);			
			}
			
			itemCtx.put(
					"lexical_value",
					control.getDisplayValue(val, props.getDatatype(),
							pContext.getLocale()));

		} catch (Exception e) {
			itemCtx.put("value", "");
			itemCtx.put("lexical_value", "");
		}
		
		String label = this.translate(control.getLabel(), pContext.getLocale());

		label = FillProcessor.processFills(label, inst, model,
				pContext.getRenderConfig(), pContext.getLocale());

		itemCtx.put("label", label);

		String hint = this.translate(control.getHint(), pContext.getLocale());

		hint = FillProcessor.processFills(hint, inst,
				model, pContext.getRenderConfig(), pContext.getLocale());

		itemCtx.put("hint", hint);

		itemCtx.put("required",
				Boolean.valueOf(NodeValidator.isRequired(props, inst, model))
						.toString());

		itemCtx.put("relevant",
				Boolean.valueOf(NodeValidator.isRelevant(props, inst, model))
						.toString());

		itemCtx.put("readonly",
				Boolean.valueOf(NodeValidator.isReadOnly(props, inst, model))
						.toString());

		// To debug or not to debug...
		if (this.debug) {
			itemCtx.put("required_expr", props.getRequired().toString());
			itemCtx.put("relevant_expr", props.getRelevant().toString());
			itemCtx.put("constraint_expr", props.getConstraint().toString());
			itemCtx.put("readonly_expr", props.getReadOnly().toString());
			itemCtx.put("required_expr_resolved",
					XRefSolver.resolve(model, inst, props.getRequired(), node));
			itemCtx.put("relevant_expr_resolved",
					XRefSolver.resolve(model, inst, props.getRelevant(), node));
			itemCtx.put("constraint_expr_resolved",
					XRefSolver.resolve(model, inst, props.getConstraint(), node));
			itemCtx.put("readonly_expr_resolved",
					XRefSolver.resolve(model, inst, props.getReadOnly(), node));
		}

		if (control instanceof Vocabulary) {

			ArrayList<Option> options = new ArrayList<Option>();
			
			for (Option opt:((Vocabulary) rItem).getOptions()) {
				options.add(new Option(opt.getValue(), this.translate(opt.getLabel(), pContext.getLocale())));
			}
			
			itemCtx.put("options", options);
		}

		// Check for error conditions. Put empty alert first.
		//
		itemCtx.put("alert", "");

		if (ActionResultImpl.FAIL.equals(pContext.getResult().toString())) {

			// Is it the data?
			Exception error = ((Failure) pContext.getResult()).getException();

			if (error instanceof ValidationException) {

				Map<String, Exception> errors = ((ValidationException) error)
						.getErrors();

				if (errors.containsKey(((Control) rItem).getBind())) {

					if ("".equals(((Control) rItem).getAlert())) {
						itemCtx.put(
								"alert",
								translateError(((ConstraintViolation) errors
										.get(((Control) rItem).getBind()))
										.getMessage(), bundle));
					} else {
						String alert = this.translate(((Control) rItem).getAlert(), pContext.getLocale());

						alert = FillProcessor.processFills(
								alert, inst, model,
								pContext.getRenderConfig(),
								pContext.getLocale());

						itemCtx.put("alert", alert);
					}
				}
			}
		}

		values.put(rItem.getId(), itemCtx);
	}

	/**
	 * Add single item to context, or, if it's a group, add it's controls.
	 * 
	 * @param rItem
	 * @param context
	 * @param pContext
	 * @param bundle
	 */
	private void addGroupToContext(final Group group,
			final RunnerContext pContext,
			final Map<String, HashMap<String, Object>> values) {

		HashMap<String, Object> itemCtx = new HashMap<String, Object>();

		if (isRelevant(group, pContext)) {
			itemCtx.put("relevant", TRUE);
		} else {
			itemCtx.put("relevant", FALSE);
		}

		Model model = pContext.getModel();
		Instance inst = pContext.getInstance();

		String label = this.translate(group.getLabel(), pContext.getLocale());
		
		label = FillProcessor.processFills(label, inst, model,
				pContext.getRenderConfig(), pContext.getLocale());

		itemCtx.put("label", label);
		
		String hint = this.translate(group.getHint(), pContext.getLocale());

		hint = FillProcessor.processFills(hint, inst, model,
				pContext.getRenderConfig(), pContext.getLocale());

		itemCtx.put("hint", hint);

		values.put(group.getId(), itemCtx);
		
		if (group instanceof Vocabulary) {

			itemCtx.put("options", ((Vocabulary) group).getOptions());
		}
	}
	
	/**
	 * Translate given translatable, nut only if message id is not empty.
	 */
	private String translate(Translatable str, Locale locale) {
		
		if ("".equals(str.getMsgid())) {
			return "";
		}
		
		String baseName = this.cfg.getString("formatter.locale.basename", "Messages");

		I18n i18n = I18nFactory.getI18n(this.getClass(), baseName, locale);

		if (!"".equals(str.getMsgctx()) && str.getMsgctx() != null) {
			return i18n.trc(str.getMsgid(), str.getMsgctx());
		} else {			
			return i18n.tr(str.getMsgid());
		}

	}

	/**
	 * Determine whether group should actually be shown or not.
	 * 
	 * @param group
	 * @param pContext
	 * @return
	 */
	private boolean isRelevant(final Group group, final RunnerContext pContext) {

		for (Renderable r : group.getItems()) {

			if (r instanceof Control) {
				Control control = (Control) r;
				String bind = control.getBind();
				Model model = pContext.getModel();
				Instance inst = pContext.getInstance();
				ItemProperties props = model.getItemProperties(bind);

				if (props == null) {
					props = new ItemPropertiesImpl(bind);
				}

				if (NodeValidator.isRelevant(props, inst, model)) {
					return true;
				}
			} else if (r instanceof TextBlock) {
				return true;
			} else if (r instanceof Group && isRelevant((Group) r, pContext)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Return the translated alert message.
	 * 
	 * @param msg
	 *            original message.
	 * @param bundle
	 *            locale bindle
	 * @return the translated message.
	 */
	private String translateError(final String msg,
			final UTF8ResourceBundle bundle) {

		try {
			if (ConstraintViolation.REQUIRED.equals(msg)) {
				return bundle.getString("alert.required");
			} else if (ConstraintViolation.TYPE.equals(msg)) {
				return bundle.getString("alert.type");
			} else if (ConstraintViolation.FALSE.equals(msg)) {
				return bundle.getString("alert.constraint");
			} else {
				return bundle.getString("alert.unknown");
			}
		} catch (Exception e) {
			return "Erroneous input";
		}
	}

	/**
	 * Offer access to the formatter's engine.
	 * 
	 * @return the Velocity engine
	 */
	public VelocityEngine getEngine() {
		return this.engine;
	}
}
