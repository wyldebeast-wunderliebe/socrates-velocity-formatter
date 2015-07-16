/*
 * File      : TestVelocityHTMLFormatter.java
 * Classname : TestVelocityHTMLFormatter
 * Author    : Duco Dokter
 * Date      : 20 Jan 2005
 * Version   : $Revision: 1.6 $
 * Copyright : Wyldebeast & Wunderliebe
 * License   : GPL
 */

package com.w20e.socrates.formatting;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.configuration.Configuration;

import com.w20e.socrates.config.ConfigurationResource;
import com.w20e.socrates.data.Instance;
import com.w20e.socrates.model.InstanceImpl;
import com.w20e.socrates.model.Model;
import com.w20e.socrates.model.ModelImpl;
import com.w20e.socrates.model.NodeImpl;
import com.w20e.socrates.process.RunnerContextImpl;
import com.w20e.socrates.rendering.Checkbox;
import com.w20e.socrates.rendering.ControlImpl;
import com.w20e.socrates.rendering.Input;
import com.w20e.socrates.rendering.RenderConfig;
import com.w20e.socrates.rendering.RenderState;
import com.w20e.socrates.rendering.Renderable;
import com.w20e.socrates.rendering.StateManager;
import com.w20e.socrates.rendering.TextBlock;
import com.w20e.socrates.rendering.TranslatableImpl;


public class TestVelocityHTMLFormatter extends TestCase {

	private Formatter formatter;

	public TestVelocityHTMLFormatter(String name) {
		super(name);
	}

	public void setUp() {
				
		try {
			System.setProperty("socrates.config.url",
					"file:./target/test-classes/test-config.xml");
			Configuration cfg = ConfigurationResource.getInstance()
					.getConfiguration();
			this.formatter = new VelocityHTMLFormatter();
			this.formatter.init(cfg);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	
	public void testFormat() {

		InstanceImpl inst = new InstanceImpl();
		ModelImpl model = new ModelImpl();
		StateManager sm = new TestStateManager();

		ArrayList<Renderable> testItems = new ArrayList<Renderable>();
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			inst.addNode(new NodeImpl("A01", "SOME VALUE"));
			inst.addNode(new NodeImpl("A02", "SOME VALUE"));
			inst.addNode(new NodeImpl("A03"));
			inst.addNode(new NodeImpl("locale"));

			ControlImpl item = new Input("c0");
			item.setBind("A01");
			item.setLabel("Yo dude");
			item.setHint(new TranslatableImpl("Modda"));

			ControlImpl item2 = new Input("c1");
			item2.setBind("A02");
			item2.setLabel("Yo dude2");
			item2.setHint(new TranslatableImpl("Modda2"));

			ControlImpl item3 = new Checkbox("c2");
			item3.setBind("A03");
			item3.setLabel("Check me!");

			TextBlock text = new TextBlock("c2");
			text.setText("Foo! <a href='http://la.la/la/${locale}/'>lala</a>");
			
			testItems.add(item);
			testItems.add(item2);
			testItems.add(item3);
			testItems.add(text);

			RunnerContextImpl ctx = new RunnerContextImpl(out, this.formatter,
					sm, model, inst, null);
			ctx.setLocale(new Locale("en", "GB"));

			// No local options
			this.formatter.format(testItems, out, ctx);
			
			assertTrue(out.toString().indexOf("enable_js: true") > -1);
			assertTrue(out.toString().indexOf("disable_ajax_validation: true") > -1);

			System.out.println(out.toString());
			assertTrue(out.toString().indexOf("Yo dude") != -1);

			assertTrue(out.toString().indexOf("Foo!") != -1);
			
			out.reset();
			
			Map<String, String> opts = new HashMap<String, String>();
			opts.put("disable_ajax_validation", "false");
			
			ctx.setProperty("renderOptions", opts);
			ctx.setLocale(new Locale("de", "DE"));
			
			this.formatter.format(testItems, out, ctx);

			assertTrue(out.toString().indexOf("enable_js: true") > -1);
			assertTrue(out.toString().indexOf("disable_ajax_validation: false") > -1);
			
			assertTrue(out.toString().indexOf("He du!") != -1);

			//assertTrue(out.toString().indexOf("Fuu!") != -1);

		} catch (Exception e) {

			fail(e.getMessage());
		}

		try {
			this.formatter.format(testItems, null, null);
			fail("Should fail here!");
		} catch (Exception e) {
			// Whatever...
		}
	}

	private static final class TestStateManager implements StateManager {

		public RenderState current() {
			return null;
		}

		public boolean hasNext() {
			return false;
		}

		public boolean hasPrevious() {
			return false;
		}

		public void init(Configuration config, RenderConfig cfg, Model m, Instance i) {

		}

		public RenderState next() {
			return null;
		}

		public RenderState previous() {
			return null;
		}

		public boolean setState(RenderState state) {
			return false;
		}
		
        public boolean setStateById(String stateId) {
            return false;
        }

        @Override
        public int getProgressPercentage() {
            return 0;
        }
	}	
}
