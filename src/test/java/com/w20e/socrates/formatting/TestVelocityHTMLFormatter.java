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

import org.apache.commons.configuration.Configuration;

import junit.framework.TestCase;

import com.w20e.socrates.config.ConfigurationResource;
import com.w20e.socrates.data.Instance;
import com.w20e.socrates.formatting.Formatter;
import com.w20e.socrates.model.InstanceImpl;
import com.w20e.socrates.model.Model;
import com.w20e.socrates.model.ModelImpl;
import com.w20e.socrates.model.NodeImpl;
import com.w20e.socrates.process.RunnerContextImpl;
import com.w20e.socrates.rendering.ControlImpl;
import com.w20e.socrates.rendering.Input;
import com.w20e.socrates.rendering.RenderConfig;
import com.w20e.socrates.rendering.RenderState;
import com.w20e.socrates.rendering.Renderable;
import com.w20e.socrates.rendering.StateManager;


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

			ControlImpl item = new Input("c0");
			item.setBind("A01");
			item.setType("input");
			item.setLabel("Yo dude");
			item.setHint("Modda");

			ControlImpl item2 = new Input("c1");
			item2.setBind("A02");
			item2.setType("input");
			item2.setLabel("Yo dude2");
			item2.setHint("Modda2");

			testItems.add(item);
			testItems.add(item2);

			RunnerContextImpl ctx = new RunnerContextImpl(out, this.formatter,
					sm, model, inst, null);
			ctx.setLocale(new Locale("en", "GB"));

			Map<String, String> opts = new HashMap<String, String>();
			opts.put("disable_ajax_validation", "false");
			
			ctx.setProperty("renderOptions", opts);
			this.formatter.format(testItems, out, ctx);

			assertTrue(out.toString().indexOf("enable_js: true") > -1);
			assertTrue(out.toString().indexOf("disable_ajax_validation: false") > -1);
			
		} catch (Exception e) {

			fail(e.getMessage());
		}

		
		// System.out.println(out.toString());
		assertTrue(out.toString().indexOf("Yo dude") != -1);

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

		public void init(RenderConfig cfg, Model m, Instance i) {

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
