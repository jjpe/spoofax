package org.metaborg.spoofax.core.processing;

import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;

import org.apache.commons.vfs2.FileObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.metaborg.runtime.task.engine.ITaskEngine;
import org.metaborg.runtime.task.engine.TaskManager;
import org.metaborg.spoofax.core.SpoofaxException;
import org.metaborg.spoofax.core.analysis.AnalysisResult;
import org.metaborg.spoofax.core.analysis.IAnalysisService;
import org.metaborg.spoofax.core.context.IContext;
import org.metaborg.spoofax.core.context.SpoofaxContext;
import org.metaborg.spoofax.core.language.AllLanguagesFileSelector;
import org.metaborg.spoofax.core.language.ILanguage;
import org.metaborg.spoofax.core.language.ILanguageIdentifierService;
import org.metaborg.spoofax.core.resource.IResourceService;
import org.metaborg.spoofax.core.syntax.ISyntaxService;
import org.metaborg.spoofax.core.syntax.ParseResult;
import org.metaborg.spoofax.core.terms.ITermFactoryService;
import org.metaborg.spoofax.core.text.ISourceTextService;
import org.spoofax.interpreter.library.index.IIndex;
import org.spoofax.interpreter.library.index.IndexManager;
import org.spoofax.interpreter.terms.ITermFactory;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;

public class OneshotProcessor<ParseT, AnalysisT> {
    private static final Logger logger = LogManager.getLogger(OneshotProcessor.class);

    private final IResourceService resourceService;
    private final ILanguageIdentifierService languageIdentifierService;
    private final ISourceTextService sourceTextService;
    private final ISyntaxService<ParseT> parseService;
    private final ITermFactoryService termFactoryService;
    private final IAnalysisService<ParseT, AnalysisT> analysisService;

    @Inject public OneshotProcessor(IResourceService resourceService,
        ILanguageIdentifierService languageIdentifierService, ISourceTextService sourceTextService,
        ISyntaxService<ParseT> parseService, ITermFactoryService termFactoryService,
        IAnalysisService<ParseT, AnalysisT> analysisService) {
        this.resourceService = resourceService;
        this.languageIdentifierService = languageIdentifierService;
        this.sourceTextService = sourceTextService;
        this.parseService = parseService;
        this.termFactoryService = termFactoryService;
        this.analysisService = analysisService;
    }

    public void process(String resourcesLocation) throws IOException {
        final FileObject resourcesDirectory = resourceService.resolve(resourcesLocation);
        final FileObject[] resources =
            resourcesDirectory.findFiles(new AllLanguagesFileSelector(languageIdentifierService));
        final Multimap<ILanguage, FileObject> allResourcesPerLang = LinkedHashMultimap.create();
        for(FileObject resource : resources) {
            final ILanguage language = languageIdentifierService.identify(resource);
            if(language != null) {
                allResourcesPerLang.put(language, resource);
            }
        }
        final int numLangs = allResourcesPerLang.keySet().size();
        final int numResources = allResourcesPerLang.values().size();

        final Multimap<ILanguage, ParseResult<ParseT>> allParseResults =
            LinkedHashMultimap.create(numLangs, numResources / numLangs);
        for(Entry<ILanguage, FileObject> entry : allResourcesPerLang.entries()) {
            final ILanguage language = entry.getKey();
            final FileObject resource = entry.getValue();

            try {
                final String sourceText = sourceTextService.text(resource);
                final ParseResult<ParseT> parseResult = parseService.parse(sourceText, resource, language);
                allParseResults.put(language, parseResult);

                // TODO: emit parse messages
            } catch(IOException e) {
                // TODO: emit error message
            }
        }

        final Multimap<ILanguage, AnalysisResult<ParseT, AnalysisT>> allAnalysisResults =
            LinkedHashMultimap.create(numLangs, numResources / numLangs);
        for(Entry<ILanguage, Collection<ParseResult<ParseT>>> entry : allParseResults.asMap().entrySet()) {
            final ILanguage language = entry.getKey();
            // TODO: create only one context per language.
            final IContext context = new SpoofaxContext(language, resourcesDirectory);
            final Iterable<ParseResult<ParseT>> parseResults = entry.getValue();
            try {
                final AnalysisResult<ParseT, AnalysisT> analysisResult =
                    analysisService.analyze(parseResults, context);
                allAnalysisResults.put(language, analysisResult);

                // TODO: emit analysis messages
            } catch(SpoofaxException e) {
                // TODO: emit error message
            }
        }

        // TODO: execute actions
        // TODO: emit action messages
    }
}
