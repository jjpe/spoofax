package org.strategoxt.imp.runtime.dynamicloading;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.imp.services.IFoldingUpdater;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.strategoxt.imp.runtime.services.FoldingUpdater;
import org.strategoxt.imp.runtime.services.NodeMapping;

import static org.strategoxt.imp.runtime.dynamicloading.TermReader.*;

/**
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class FoldingUpdaterFactory extends AbstractServiceFactory<IFoldingUpdater> {
	
	@Override
	public Class<IFoldingUpdater> getCreatedType() {
		return IFoldingUpdater.class;
	}

	/**
	 * @see Descriptor#createService(Class)
	 */
	@Override
	public IFoldingUpdater create(Descriptor d) throws BadDescriptorException {
		// TODO: "FoldAll" folding rules
		
		List<NodeMapping> folded = new ArrayList<NodeMapping>(); 
		List<NodeMapping> defaultFolded = new ArrayList<NodeMapping>(); 
		Object foldme = new Object();
		
		for (IStrategoAppl folding : collectTerms(d.getDocument(), "FoldRule")) {
			IStrategoAppl term = termAt(folding, 1);
			String type = term.getConstructor().getName();
			NodeMapping mapping = NodeMapping.create(folding, foldme);
			
			if (type.equals("None")) {
				folded.add(mapping);
			} else if (type.equals("Disable")) {
				folded.remove(mapping);
				defaultFolded.remove(mapping);
			} else {
				defaultFolded.add(mapping);
			}
		}
		
		
		return new FoldingUpdater(folded, defaultFolded);
	}
}