package org.nasdanika.demo.drawio.semanticmapping.tests;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.emf.common.util.DiagnosticException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.jupiter.api.Test;
import org.nasdanika.common.BiSupplier;
import org.nasdanika.common.Context;
import org.nasdanika.common.ExecutionException;
import org.nasdanika.common.NasdanikaException;
import org.nasdanika.common.ProgressMonitor;
import org.nasdanika.html.model.app.Label;
import org.nasdanika.html.model.app.Link;
import org.nasdanika.html.model.app.gen.SemanticMapResourceFactory;
import org.nasdanika.html.model.app.gen.SemanticSiteGenerator;
import org.nasdanika.ncore.ModelElement;

public class TestDrawioSemanticMappingGenerator {
	
	@Test
	public void generate() throws Exception {
		//new DrawioSemanticMappingGeneratorRefactored().generate();

		URI semanticModelURI = URI.createFileURI(new File("model/high-level-architecture.drawio").getAbsolutePath());
			
		String rootActionResource = "model/root-action.yml";
		URI rootActionURI = URI.createFileURI(new File(rootActionResource).getAbsolutePath());//.appendFragment("/");
		
		String pageTemplateResource = "model/page-template.yml";
		URI pageTemplateURI = URI.createFileURI(new File(pageTemplateResource).getAbsolutePath());//.appendFragment("/");
		
		String siteMapDomain = "https://docs.nasdanika.org/demo-drawio-semantic-mapping";
		
		URI semanticMapURI = URI.createURI("https://docs.nasdanika.org/demo-action-site/semantic-map.json");				

		SemanticSiteGenerator siteGenerator = new SemanticSiteGenerator() {
			
			Map<ModelElement, Label> semanticMap = new LinkedHashMap<>();			
			
			@Override
			protected ResourceSet createResourceSet(Context context, ProgressMonitor progressMonitor) {
				ResourceSet resourceSet = super.createResourceSet(context, progressMonitor);
				SemanticMapResourceFactory smrf = new SemanticMapResourceFactory() {
					@Override
					protected void onLoad(Map<ModelElement, Label> resourceSemanticMap, Resource resource) {
						super.onLoad(resourceSemanticMap, resource);
						semanticMap.putAll(resourceSemanticMap);
					}
				};
				resourceSet.getResourceFactoryRegistry().getProtocolToFactoryMap().put("semantic-map", smrf);				
				try {
					URI sMapURI = URI.createURI("semantic-map:" + URLEncoder.encode(semanticMapURI.toString(), StandardCharsets.UTF_8.name()));
					resourceSet.getResource(sMapURI, true);
				} catch (UnsupportedEncodingException e) {
					throw new NasdanikaException(e);
				}
				
				return resourceSet;
			}			
			
			@Override
			protected BiSupplier<Resource, Map<EObject, Label>> generateActionModel(
					Resource semanticModelResource,	
					URI actionModelURI, 
					Context context, 
					ProgressMonitor progressMonitor) throws DiagnosticException, IOException {
				BiSupplier<Resource, Map<EObject, Label>> result = super.generateActionModel(semanticModelResource, actionModelURI, context, progressMonitor);
				Map<EObject, Label> compositeRegistry = new HashMap<>(semanticMap);
				compositeRegistry.putAll(result.getSecond());
				return BiSupplier.of(result.getFirst(), compositeRegistry);
			}
			
			@Override
			protected boolean isSemanticMapLink(Link link) {
				return semanticMap.values().contains(link);
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
