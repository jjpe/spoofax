package org.strategoxt.imp.runtime.parser;

import java.io.IOException;
import java.io.InputStream;

import org.spoofax.jsglr.BadTokenException;
import org.spoofax.jsglr.IRecoverAlgorithm;
import org.spoofax.jsglr.ParseTable;
import org.spoofax.jsglr.SGLR;
import org.spoofax.jsglr.SGLRException;
import org.spoofax.jsglr.TokenExpectedException;
import org.strategoxt.imp.runtime.Environment;
import org.strategoxt.imp.runtime.parser.tokens.TokenKindManager;

import aterm.ATerm;

/**
 * IMP IParser implementation using JSGLR, imploding parse trees to AST nodes and tokens.
 *
 * @author Lennart Kats <L.C.L.Kats add tudelft.nl>
 */ 
public class JSGLRI extends AbstractSGLRI {
	
	private final ParseTable parseTable;
	
	private SGLR parser;
	
	// Initialization and parsing
	
	public JSGLRI(ParseTable parseTable, String startSymbol,
			SGLRParseController controller, TokenKindManager tokenManager) {
		super(controller, tokenManager, startSymbol, parseTable);
		
		this.parseTable = parseTable;
		resetState();
	}
	
	public JSGLRI(ParseTable parseTable, String startSymbol) {
		this(parseTable, startSymbol, null, new TokenKindManager());
	}
	
	@Deprecated
	public void withBacktracking(boolean withBT) {
		parser.withBacktracking(withBT);
    }
	
	public void asyncAbort() {
		parser.asyncAbort();
	}
	
	public void setRecoverHandler(IRecoverAlgorithm recoverHandler) {
		parser.setRecoverHandler(recoverHandler);
	}
	
	@Override
	protected ATerm doParseNoImplode(char[] inputChars, String filename)
			throws TokenExpectedException, BadTokenException, SGLRException, IOException {
		
		return doParseNoImplode(toByteStream(inputChars), inputChars);
	}
	
	/**
	 * Resets the state of this parser, reinitializing the SGLR instance
	 */
	void resetState() {
		parser = Environment.createSGLR(parseTable);
		parser.setCycleDetect(false);
		parser.setFilter(false); // FIXME: Filters not supported ATM
	}
	
	private ATerm doParseNoImplode(InputStream inputStream, char[] inputChars)
			throws TokenExpectedException, BadTokenException, SGLRException, IOException {
		
		// FIXME: Some bug in JSGLR is causing its state to get corrupted; must reset it every parse
		resetState();
		
		// Read stream using tokenizer/lexstream
		
		// TODO: Once spoofax supports it, use the start symbol
		ATerm asfix = parser.parse(inputStream, null /*getStartSymbol()*/); 
		
		return asfix;
	}
}