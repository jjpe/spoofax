package org.strategoxt.imp.runtime.services.menus;

import static org.spoofax.interpreter.core.Tools.termAt;
import static org.strategoxt.imp.runtime.dynamicloading.TermReader.collectTerms;
import static org.strategoxt.imp.runtime.dynamicloading.TermReader.cons;
import static org.strategoxt.imp.runtime.dynamicloading.TermReader.termContents;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.URIUtil;
import org.eclipse.imp.editor.UniversalEditor;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.jface.resource.ImageDescriptor;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.strategoxt.imp.runtime.EditorState;
import org.strategoxt.imp.runtime.Environment;
import org.strategoxt.imp.runtime.dynamicloading.AbstractServiceFactory;
import org.strategoxt.imp.runtime.dynamicloading.BadDescriptorException;
import org.strategoxt.imp.runtime.dynamicloading.Descriptor;
import org.strategoxt.imp.runtime.parser.SGLRParseController;
import org.strategoxt.imp.runtime.services.StrategoObserver;
import org.strategoxt.imp.runtime.services.menus.contribs.CustomStrategyBuilder;
import org.strategoxt.imp.runtime.services.menus.contribs.DebugModeBuilder;
import org.strategoxt.imp.runtime.services.menus.contribs.IBuilder;
import org.strategoxt.imp.runtime.services.menus.contribs.Menu;
import org.strategoxt.imp.runtime.services.menus.contribs.Separator;
import org.strategoxt.imp.runtime.services.menus.contribs.StrategoBuilder;

/**
 * @author Lennart Kats <lennart add lclnet.nl>
 * @author Oskar van Rest
 */
public class MenuFactory extends AbstractServiceFactory<IMenuList> {

	public MenuFactory() {
		super(IMenuList.class, false); // not cached; depends on derived editor relation
	}

	@Override
	public IMenuList create(Descriptor d, SGLRParseController controller) throws BadDescriptorException {
		List<Menu> menus = new LinkedList<Menu>();

		EditorState derivedFromEditor = getDerivedFromEditor(controller);

		if (d.isATermEditor() && derivedFromEditor != null)
			addDerivedMenus(derivedFromEditor, menus);

		addMenus(d, controller, menus, null);
		addCustomStrategyBuilder(d, controller, menus, derivedFromEditor);
		if (Environment.allowsDebugging(d)) // Descriptor allows debugging)
		{
			addDebugModeBuilder(d, controller, menus, derivedFromEditor);
		}
		return new MenuList(menus);
	}

	private static void addMenus(Descriptor d, SGLRParseController controller, List<Menu> menus, EditorState derivedFromEditor) throws BadDescriptorException {

		// BEGIN: 'Transform' menu backwards compatibility
		ArrayList<IStrategoAppl> builders = collectTerms(d.getDocument(), "Builder");
		for (IStrategoAppl b : builders) {
			String caption = termContents(termAt(b, 0));
			List<String> path = createPath(createPath(Collections.<String> emptyList(), MenusServiceConstants.OLD_LABEL), caption);
			IBuilder builder = createBuilder(b, path, d, controller, derivedFromEditor);
			if (builder != null) {
				Menu menu = null;
				for (Menu m : menus) {
					if (m.getCaption().equals(MenusServiceConstants.OLD_LABEL)) {
						menu = m;
					}
				}

				if (menu == null) {
					menu = new Menu(MenusServiceConstants.OLD_LABEL);
					menus.add(0, menu);
				}

				menu.addMenuContribution(builder);
			}
		}
		// END: 'Transform' menu backwards compatibility

		for (IStrategoAppl m : collectTerms(d.getDocument(), "ToolbarMenu")) {
			String caption = termContents(termAt(m, 0)); // caption = label or icon path
			Menu menu = new Menu(caption);

			if (((IStrategoAppl) termAt(m, 0)).getConstructor().getName().equals("Icon")) {
				String iconPath = caption;
				String pluginPath = d.getBasePath().toOSString();
				File iconFile = new File(pluginPath, iconPath);
				if (iconFile.exists()) {
					ImageDescriptor imageDescriptor = null;
					try {
						imageDescriptor = ImageDescriptor.createFromURL(URIUtil.toURL(iconFile.toURI()));
					} catch (MalformedURLException e) {
						e.printStackTrace();
					}
					menu.setIcon(imageDescriptor);
				}
			}

			List<String> path = createPath(Collections.<String> emptyList(), caption);
			addMenuContribs(menu, m.getSubterm(2), path, d, controller, derivedFromEditor); // TODO: 2 --> 1
			menus.add(menu);
		}
	}

	private static List<String> createPath(List<String> init, String last) {
		List<String> result = new LinkedList<String>(init);
		result.add(last);
		return result;
	}

	private static void addMenuContribs(Menu menu, IStrategoTerm menuContribs, List<String> path, Descriptor d, SGLRParseController controller, EditorState derivedFromEditor) throws BadDescriptorException {

		for (IStrategoAppl a : collectTerms(menuContribs, "Action", "Separator", "Submenu")) {
			String cons = a.getConstructor().getName();

			if (cons.equals("Action")) {
				String caption = termContents(termAt(a, 0));
				IBuilder builder = createBuilder(a, createPath(path, caption), d, controller, derivedFromEditor);
				if (builder != null) {
					menu.addMenuContribution(builder);
				}
			} else if (cons.equals("Separator")) {
				menu.addMenuContribution(new Separator());
			} else if (cons.equals("Submenu")) {
				String caption = termContents(termAt(a, 0));
				Menu submenu = new Menu(caption);
				addMenuContribs(submenu, a.getSubterm(1), createPath(path, caption), d, controller, derivedFromEditor);
				menu.addMenuContribution(submenu);
			}
		}
	}

	private static IBuilder createBuilder(IStrategoTerm action, List<String> path, Descriptor d, SGLRParseController controller, EditorState derivedFromEditor) throws BadDescriptorException {
		
		StrategoObserver feedback = d.createService(StrategoObserver.class, controller);
		String strategy = termContents(termAt(action, 1));
		IStrategoList options = termAt(action, 2);

		boolean openEditor = false;
		boolean realTime = false;
		boolean persistent = false;
		boolean meta = false;
		boolean cursor = false;
		boolean source = false;

		for (IStrategoTerm option : options.getAllSubterms()) {
			String type = cons(option);
			if (type.equals("OpenEditor")) {
				openEditor = true;
			} else if (type.equals("RealTime")) {
				realTime = true;
			} else if (type.equals("Persistent")) {
				persistent = true;
			} else if (type.equals("Meta")) {
				meta = true;
			} else if (type.equals("Cursor")) {
				cursor = true;
			} else if (type.equals("Source")) {
				source = true;
			} else {
				throw new BadDescriptorException("Unknown builder annotation: " + type);
			}
		}
		if (!meta || d.isDynamicallyLoaded())
			return new StrategoBuilder(feedback, path, strategy, openEditor, realTime, cursor, source, persistent, derivedFromEditor);
		else
			return null;
	}

	private static void addDerivedMenus(EditorState derivedFromEditor, List<Menu> menus) throws BadDescriptorException {
		if (derivedFromEditor != null) {
			addMenus(derivedFromEditor.getDescriptor(), derivedFromEditor.getParseController(), menus, derivedFromEditor);
		}
	}

	private static void addCustomStrategyBuilder(Descriptor d, SGLRParseController controller, List<Menu> menus, EditorState derivedFromEditor) throws BadDescriptorException {

		if (d.isATermEditor() && derivedFromEditor != null) {
			StrategoObserver feedback = derivedFromEditor.getDescriptor().createService(StrategoObserver.class, controller);
			addCustomStrategyBuilderHelper(menus, feedback, derivedFromEditor);
		} else if (d.isDynamicallyLoaded()) {
			StrategoObserver feedback = d.createService(StrategoObserver.class, controller);
			addCustomStrategyBuilderHelper(menus, feedback, null);
		}
	}

	private static void addCustomStrategyBuilderHelper(List<Menu> menus, StrategoObserver feedback, EditorState derivedFromEditor) {
		for (Menu menu : menus) {
			List<String> path = new LinkedList<String>();
			path.add(menu.getCaption());
			path.add(CustomStrategyBuilder.CAPTION);
			menu.addMenuContribution(new Separator());
			menu.addMenuContribution(new CustomStrategyBuilder(path, feedback, derivedFromEditor));
		}
	}

	/**
	 * Adds a Debug Mode Builder, if debug mode is allowed the user can choose to enable stratego
	 * debugging. If debugging is enabled, a new JVM is started for every strategy invoke resulting
	 * in major performance drops. The user can also disable Debug mode, without needing to rebuil
	 * the project.
	 */
	private static void addDebugModeBuilder(Descriptor d, SGLRParseController controller, List<Menu> menus, EditorState derivedFromEditor) throws BadDescriptorException {
		StrategoObserver feedback = d.createService(StrategoObserver.class, controller);
		for (Menu menu : menus) {
			List<String> path = new LinkedList<String>();
			path.add(menu.getCaption());
			path.add(DebugModeBuilder.getCaption(feedback));
			menu.addMenuContribution(new DebugModeBuilder(feedback, path));
		}
	}

	private static EditorState getDerivedFromEditor(SGLRParseController controller) {
		if (controller.getEditor() == null || controller.getEditor().getEditor() == null)
			return null;
		UniversalEditor editor = controller.getEditor().getEditor();
		StrategoBuilderListener listener = StrategoBuilderListener.getListener(editor);
		if (listener == null)
			return null;
		UniversalEditor sourceEditor = listener.getSourceEditor();
		if (sourceEditor == null)
			return null;
		return EditorState.getEditorFor(sourceEditor.getParseController());
	}

	public static void eagerInit(Descriptor descriptor, IParseController parser, EditorState lastEditor) {
		// Refresh toolbar menu commands after rebuilding.
		MenusServiceUtil.refreshToolbarMenuCommands();
	}
}