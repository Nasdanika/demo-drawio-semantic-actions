package org.nasdanika.demo.drawio.semanticmapping.tests;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.Test;
import org.nasdanika.common.ExecutionException;
import org.nasdanika.common.Util;
import org.nasdanika.html.model.app.Link;
import org.nasdanika.html.model.app.gen.SemanticSiteGenerator;
import org.nasdanika.ncore.util.SemanticInfo;
import org.nasdanika.ncore.util.SemanticRegistry;

public class TestDrawioSemanticMappingGenerator {
	
	@Test
	public void generate() throws Exception {

		URI semanticModelURI = URI.createFileURI(new File("model/high-level-architecture.drawio").getAbsolutePath());
			
		String rootActionResource = "model/root-action.yml";
		URI rootActionURI = URI.createFileURI(new File(rootActionResource).getAbsolutePath());//.appendFragment("/");
		
		String pageTemplateResource = "model/page-template.yml";
		URI pageTemplateURI = URI.createFileURI(new File(pageTemplateResource).getAbsolutePath());//.appendFragment("/");
		
		String siteMapDomain = "https://docs.nasdanika.org/demo-drawio-semantic-mapping";
		
		SemanticRegistry semanticRegistry = new SemanticRegistry();		
		semanticRegistry.load(new URL("https://docs.nasdanika.org/demo-action-site/semantic-info.json"));
		
		SemanticSiteGenerator siteGenerator = new SemanticSiteGenerator() {
									
			@Override
			protected Iterable<SemanticInfo> getSemanticInfos() {
				return semanticRegistry
					.stream()
					.filter(SemanticInfo.class::isInstance)
					.map(SemanticInfo.class::cast)
					.collect(Collectors.toList());
			}
			
			@Override
			protected boolean isSemanticInfoLink(Link link) {
				if (link == null || Util.isBlank(link.getLocation())) {
					return false;
				}
				String linkLocation = link.getLocation();
				return semanticRegistry
					.stream()
					.filter(SemanticInfo.class::isInstance)
					.map(SemanticInfo.class::cast)
					.map(SemanticInfo::getLocation)
					.filter(Objects::nonNull)
					.map(Object::toString)
					.filter(linkLocation::equals)
					.findFirst()
					.isPresent();
			}			
			
		};
		
		Map<String, Collection<String>> errors = siteGenerator.generate(
				semanticModelURI,
				rootActionURI,
				pageTemplateURI,
				siteMapDomain,
				new File("docs"),							
				new File("target/model-doc"),
				false);
				
		int errorCount = 0;
		for (Entry<String, Collection<String>> ee: errors.entrySet()) {
			System.err.println(ee.getKey());
			for (String error: ee.getValue()) {
				System.err.println("\t" + error);
				++errorCount;
			}
		}
		
		System.out.println("There are " + errorCount + " site errors");
		
		if (errors.size() != 3) {
			throw new ExecutionException("There are problems with pages: " + errorCount);
		}		
	}

}
