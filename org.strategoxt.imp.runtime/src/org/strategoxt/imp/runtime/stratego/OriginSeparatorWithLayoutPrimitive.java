package org.strategoxt.imp.runtime.stratego;

import static org.spoofax.jsglr.client.imploder.ImploderAttachment.getLeftToken;
import static org.spoofax.jsglr.client.imploder.ImploderAttachment.getRightToken;
import static org.spoofax.jsglr.client.imploder.ImploderAttachment.getTokenizer;
import static org.spoofax.terms.attachments.ParentAttachment.getParent;

import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;
import org.spoofax.jsglr.client.imploder.IToken;
import org.spoofax.jsglr.client.imploder.ITokenizer;
import org.strategoxt.imp.runtime.parser.ast.StrategoSubList;

/**
 * @author Maartje de Jonge
 */
public class OriginSeparatorWithLayoutPrimitive extends AbstractOriginPrimitive {

	public OriginSeparatorWithLayoutPrimitive() {
		super("SSL_EXT_origin_separator_with_lo");
	}

	/**
	 * Returns the separator and its location plus layout 
	 * after a list element or sublist (before in case of last element(s)).
	 * Returns null if the separator can not be found.
	 */
	@Override
	protected IStrategoTerm call(IContext env, IStrategoTerm origin) {
		IStrategoTerm originNode=origin;
		StrategoSubList sublist;
		IStrategoTerm left;
		IStrategoTerm right;
		if(originNode instanceof StrategoSubList){
			sublist = (StrategoSubList) originNode;							
		}
		else{
			IStrategoTerm parent = getParent(origin);
			if(parent == null || !parent.isList())
				return null;
			sublist = StrategoSubList.createSublist((IStrategoList) parent, originNode, originNode, true);
		}
		IStrategoList list = sublist.getCompleteList();
		int lastIndexList = list.getSubtermCount()-1;
		boolean atEnd=false;
		if(sublist.getIndexEnd() < lastIndexList){
			left = sublist.getLastChild();
			right = list.getSubterm(sublist.getIndexEnd()+1);
		}
		else if(sublist.getIndexStart() > 0){
			right = sublist.getFirstChild();
			left = list.getSubterm(sublist.getIndexStart()-1);
			atEnd=true;
		}
		else
			return null; //complete list has no separator
		
		ITokenizer tokens=getTokenizer(left);
		int startTokenSearch = getRightToken(left).getIndex()+1;
		int endTokenSearch = getLeftToken(right).getIndex()-1;
		int startSeparation=-1;
		int endSeparation=-1;
		int loopIndex=startTokenSearch;
		while (loopIndex <= endTokenSearch) {
			IToken tok = tokens.getTokenAt(loopIndex);
			if(!DocumentStructure.isLayoutToken(tok)){
				if(startSeparation==-1)
					startSeparation=tok.getStartOffset();
				endSeparation=tok.getEndOffset()+1;
			}
			loopIndex++;
		}
		ITokenizer lex = getTokenizer(originNode);
		int endOfSep=endSeparation;
		int startOfSep=startSeparation;
		if(startSeparation!=-1){
			if(sublist.getIndexStart() > 0){
				String potentialLayout = lex.toString(getRightToken(left).getEndOffset()+1, startOfSep-1);
				int newlineIndex=potentialLayout.lastIndexOf('\n');
				if(newlineIndex>=0){
					startOfSep=getRightToken(left).getEndOffset()+1+newlineIndex+1;
				}
			}
			if(!atEnd){
				String potentialLayout_r = lex.toString(endOfSep, getLeftToken(right).getStartOffset()-1);
				int newlineIndex_r=potentialLayout_r.lastIndexOf('\n');
				if(newlineIndex_r>=0){
					endOfSep=endOfSep+1+newlineIndex_r;
				}
			}		
			ITermFactory factory = env.getFactory();
			return factory.makeTuple(
					factory.makeInt(startOfSep),
					factory.makeInt(endOfSep),
					factory.makeString(lex.toString(startOfSep, endOfSep-1))
			);
		}
		return null;
	}
}