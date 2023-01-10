package org.nasdanika.demo.drawio.semanticmapping;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.xml.transform.TransformerException;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.nasdanika.common.BiSupplier;
import org.nasdanika.common.Context;
import org.nasdanika.common.DefaultConverter;
import org.nasdanika.common.Diagnostic;
import org.nasdanika.common.DiagnosticException;
import org.nasdanika.common.ExecutionException;
import org.nasdanika.common.MutableContext;
import org.nasdanika.common.NasdanikaException;
import org.nasdanika.common.PrintStreamProgressMonitor;
import org.nasdanika.common.ProgressMonitor;
import org.nasdanika.common.PropertyComputer;
import org.nasdanika.common.Status;
import org.nasdanika.common.SupplierFactory;
import org.nasdanika.drawio.Connection;
import org.nasdanika.drawio.Layer;
import org.nasdanika.drawio.LayerElement;
import org.nasdanika.drawio.Page;
import org.nasdanika.drawio.comparators.LabelModelElementComparator;
import org.nasdanika.emf.EObjectAdaptable;
import org.nasdanika.emf.EmfUtil;
import org.nasdanika.emf.persistence.NcoreObjectLoaderSupplier;
import org.nasdanika.exec.content.ContentFactory;
import org.nasdanika.exec.resources.Container;
import org.nasdanika.exec.resources.ReconcileAction;
import org.nasdanika.exec.resources.ResourcesFactory;
import org.nasdanika.html.HTMLFactory;
import org.nasdanika.html.Tag;
import org.nasdanika.html.TagName;
import org.nasdanika.html.emf.ActionProviderAdapterFactory;
import org.nasdanika.html.emf.EObjectActionResolver;
import org.nasdanika.html.emf.NcoreActionBuilder;
import org.nasdanika.html.jstree.JsTreeFactory;
import org.nasdanika.html.jstree.JsTreeNode;
import org.nasdanika.html.model.app.Action;
import org.nasdanika.html.model.app.AppFactory;
import org.nasdanika.html.model.app.Label;
import org.nasdanika.html.model.app.Link;
import org.nasdanika.html.model.app.gen.ActionContentProvider;
import org.nasdanika.html.model.app.gen.AppAdapterFactory;
import org.nasdanika.html.model.app.gen.LinkJsTreeNodeSupplierFactoryAdapter;
import org.nasdanika.html.model.app.gen.NavigationPanelConsumerFactoryAdapter;
import org.nasdanika.html.model.app.gen.Util;
import org.nasdanika.html.model.app.util.ActionProvider;
import org.nasdanika.html.model.html.gen.ContentConsumer;
import org.nasdanika.ncore.util.NcoreUtil;
import org.nasdanika.resources.FileSystemContainer;

import com.redfin.sitemapgenerator.ChangeFreq;

public class DrawioSemanticMappingGeneratorRefactored {
	
	/**
	 * Creates a resource set for loading the semantic model. 
	 * Override to customize, e.g. add a resource factory specific for the semantic model.
	 * @param progressMonitor
	 * @return
	 */
	protected ResourceSet createSemanticModelResourceSet(Context context, ProgressMonitor progressMonitor) {
		return createResourceSet(context, progressMonitor);
	}
	
	/**
	 * Creates a resource set for loading models.
	 * Override to customize, e.g. register {@link EPackage}'s and adapter factories.
	 * @param progressMonitor
	 * @return
	 */
	protected ResourceSet createResourceSet(Context context, ProgressMonitor progressMonitor) {
		return Util.createResourceSet(context, progressMonitor);
	}	
	
	/**
	 * Loads a model using {@link NcoreObjectLoaderSupplier} which supports YAML, JSON, and. Optionally creates a copy and stores to XMI.
	 * @param uri Model URI
	 * @param copyURI Copy URI. If not null, the loaded model is copied and saved to this URI.
	 * @param context
	 * @param progressMonitor
	 * @return Loaded model
	 * @throws Exception
	 */
	protected Resource loadSemanticModel(
			URI uri,
			URI copyURI,
			Context context, 
			ProgressMonitor progressMonitor) throws IOException {		
		ResourceSet resourceSet = Util.createResourceSet(context, progressMonitor);		
		Resource resource = resourceSet.getResource(uri, true);

		if (copyURI == null) {
			return resource;
		}
		
		Resource copyResource = resourceSet.createResource(copyURI);
		copyResource.getContents().addAll(EcoreUtil.copyAll(resource.getContents()));
		copyResource.save(null);
		return copyResource;
	}
		
	/**
	 * Adapts the semantic model to an action model.
	 * @throws org.eclipse.emf.common.util.DiagnosticException 
	 * @throws IOException 
	 * @throws Exception
	 */
	
	public BiSupplier<Resource, Map<EObject,Action>>  generateActionModel(
			Resource semanticModelResource,
			URI actionModelURI,
			Context context, 
			ProgressMonitor progressMonitor) throws org.eclipse.emf.common.util.DiagnosticException, IOException {
		ResourceSet semanticModelResourceSet = semanticModelResource.getResourceSet();
		org.eclipse.emf.common.util.Diagnostic instanceDiagnostic = org.nasdanika.emf.EmfUtil.resolveClearCacheAndDiagnose(semanticModelResourceSet, context);
		int severity = instanceDiagnostic.getSeverity();
		if (severity != org.eclipse.emf.common.util.Diagnostic.OK) {
			EmfUtil.dumpDiagnostic(instanceDiagnostic, 2, System.err);
			throw new org.eclipse.emf.common.util.DiagnosticException(instanceDiagnostic);
		}
		
		semanticModelResourceSet.getAdapterFactories().add(new ActionProviderAdapterFactory(context) {
			
			private void collect(Notifier target, org.eclipse.emf.common.util.Diagnostic source, Collection<org.eclipse.emf.common.util.Diagnostic> accumulator) {
				List<?> data = source.getData();
				if (source.getChildren().isEmpty()
						&& source.getSeverity() > org.eclipse.emf.common.util.Diagnostic.OK 
						&& data != null 
						&& data.size() == 1 
						&& data.get(0) == target) {
					accumulator.add(source);
				}
				for (org.eclipse.emf.common.util.Diagnostic child: source.getChildren()) {
					collect(target, child, accumulator);
				}
			}
			
			protected Collection<org.eclipse.emf.common.util.Diagnostic> getDiagnostic(Notifier target) {
				Collection<org.eclipse.emf.common.util.Diagnostic> ret = new ArrayList<>();
				collect(target, instanceDiagnostic, ret);
				return ret;
			}
			
			private void collect(Notifier target, EStructuralFeature feature, org.eclipse.emf.common.util.Diagnostic source, Collection<org.eclipse.emf.common.util.Diagnostic> accumulator) {
				List<?> data = source.getData();
				if (source.getChildren().isEmpty() 
						&& source.getSeverity() > org.eclipse.emf.common.util.Diagnostic.OK 
						&& data != null 
						&& data.size() > 1 
						&& data.get(0) == target 
						&& data.get(1) == feature) {
					accumulator.add(source);
				}
				for (org.eclipse.emf.common.util.Diagnostic child: source.getChildren()) {
					collect(target, feature, child, accumulator);
				}
			}

			protected Collection<org.eclipse.emf.common.util.Diagnostic> getFeatureDiagnostic(Notifier target, EStructuralFeature feature) {
				Collection<org.eclipse.emf.common.util.Diagnostic> ret = new ArrayList<>();
				collect(target, feature, instanceDiagnostic, ret);
				return ret;
			}
			
		});
		
		ResourceSet actionModelsResourceSet = createResourceSet(context, progressMonitor);
		
		org.eclipse.emf.ecore.resource.Resource actionModelResource = actionModelsResourceSet.createResource(actionModelURI);
		
		Map<EObject,Action> registry = new HashMap<>();
		for (EObject semanticElement: semanticModelResource.getContents()) {
			Action action = EObjectAdaptable.adaptTo(semanticElement, ActionProvider.class).execute(registry::put, progressMonitor);
			Context uriResolverContext = Context.singleton(Context.BASE_URI_PROPERTY, URI.createURI("temp://" + UUID.randomUUID() + "/" + UUID.randomUUID() + "/"));
			BiFunction<Label, URI, URI> uriResolver = org.nasdanika.html.model.app.util.Util.uriResolver(action, uriResolverContext);
			Adapter resolver = EcoreUtil.getExistingAdapter(action, EObjectActionResolver.class);
			if (resolver instanceof EObjectActionResolver) {														
				org.nasdanika.html.emf.EObjectActionResolver.Context resolverContext = new org.nasdanika.html.emf.EObjectActionResolver.Context() {
	
					@Override
					public Action getAction(EObject semanticElement) {
						return registry.get(semanticElement);
					}
	
					@Override
					public URI resolve(Action action, URI base) {
						return uriResolver.apply(action, base);
					}
					
				};
				((EObjectActionResolver) resolver).execute(resolverContext, progressMonitor);
			}
			actionModelResource.getContents().add(action);
		}
		actionModelResource.save(null);
		
		return BiSupplier.of(actionModelResource, registry);
	}
	
	/**
	 * Generates a resource model from an action model.
	 * @throws IOException 
	 * @throws Exception
	 */
	public Resource generateResourceModel(
			Resource actionResource, 
			Map<EObject, Action> registry,
			URI rootActionURI,
			URI pageTemplateURI,
			URI resourceURI, 
			String containerName,
			File resourceWorkDir,
			Context context, 
			ProgressMonitor progressMonitor) throws IOException {
		
		java.util.function.Consumer<Diagnostic> diagnosticConsumer = diagnostic -> {
			if (diagnostic.getStatus() == Status.FAIL || diagnostic.getStatus() == Status.ERROR) {
				System.err.println("***********************");
				System.err.println("*      Diagnostic     *");
				System.err.println("***********************");
				diagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);
			}
			if (diagnostic.getStatus() != Status.SUCCESS) {
				throw new DiagnosticException(diagnostic);
			};
		};
		
		Context rootActionContext = context.compose(Context.singleton("action-resource", actionResource.getURI().toString()));
		ResourceSet rootActionResourceSet = createResourceSet(rootActionContext, progressMonitor);
		Action root = (Action) rootActionResourceSet.getEObject(rootActionURI, true);
		
		Container container = ResourcesFactory.eINSTANCE.createContainer();
		container.setName(containerName);
		container.setReconcileAction(ReconcileAction.OVERWRITE);
		
		ResourceSet resourceSet = createResourceSet(context, progressMonitor);
		Resource modelResource = resourceSet.createResource(resourceURI);
		modelResource.getContents().add(container);
		
		org.nasdanika.html.model.bootstrap.Page pageTemplate = (org.nasdanika.html.model.bootstrap.Page) actionResource.getResourceSet().getEObject(pageTemplateURI, true);
		
		// Generating content file from action content 
		
		File pagesDir = new File(resourceWorkDir, "pages");
		pagesDir.mkdirs();
		
		Util.generateSite(
				root, 
				pageTemplate,
				container,
				contentProviderContext -> (cAction, uriResolver, pMonitor) -> getActionContent(cAction, uriResolver, registry, resourceWorkDir, contentProviderContext, diagnosticConsumer, pMonitor),
				contentProviderContext -> (page, baseURI, uriResolver, pMonitor) -> getPageContent(page, baseURI, uriResolver, pagesDir, contentProviderContext, progressMonitor),
				context,
				progressMonitor);
		
		modelResource.save(null);
		
		// Page model to XML conversion
		ResourceSet pageResourceSet = new ResourceSetImpl();
		pageResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());			
		pageResourceSet.getAdapterFactories().add(new AppAdapterFactory());
		for (File pageFile: pagesDir.listFiles(f -> f.getName().endsWith(".xml"))) {
			URI pageURI = URI.createFileURI(pageFile.getCanonicalPath());
			Resource pageEResource = pageResourceSet.getResource(pageURI, true);
			SupplierFactory<InputStream> contentFactory = org.nasdanika.common.Util.asInputStreamSupplierFactory(pageEResource.getContents());			
			try (InputStream contentStream = org.nasdanika.common.Util.call(contentFactory.create(context), progressMonitor, diagnosticConsumer, Status.FAIL, Status.ERROR)) {
				Files.copy(contentStream, new File(pageFile.getCanonicalPath().replace(".xml", ".html")).toPath(), StandardCopyOption.REPLACE_EXISTING);
				progressMonitor.worked(1, "[Page xml -> html] " + pageFile.getName());
			}
		}
		
		return modelResource;
	}
	
	/**
	 * Creates a file with .xml extension containing page contents resource model. Creates and returns a resource with .html extension. 
	 * A later processing step shall convert .xml to .html 
	 * @param page
	 * @param baseURI
	 * @param uriResolver
	 * @param pagesDir
	 * @param progressMonitor
	 * @return
	 */
	protected EList<EObject> getPageContent(
			org.nasdanika.html.model.bootstrap.Page page, 
			URI baseURI, 
			BiFunction<Label, URI, URI> uriResolver,
			File pagesDir,
			Context contentProviderContext,
			ProgressMonitor progressMonitor) {
		
		try {
			// Saving a page to .xml and creating a reference to .html; Pages shall be processed from .xml to .html individually.
			page.setUuid(UUID.randomUUID().toString());
			
			ResourceSet pageResourceSet = new ResourceSetImpl();
			pageResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());			
			URI pageURI = URI.createFileURI(new File(pagesDir, page.getUuid() + ".xml").getCanonicalPath());
			Resource pageEResource = pageResourceSet.createResource(pageURI);
			pageEResource.getContents().add(page);
			pageEResource.save(null);
			
			org.nasdanika.exec.content.Resource pageResource = ContentFactory.eINSTANCE.createResource();
			pageResource.setLocation("pages/" + page.getUuid() + ".html");
			progressMonitor.worked(1, "[Page content] " + page.getName() + " -> " + pageResource.getLocation());
			return ECollections.singletonEList(pageResource);
		} catch (IOException e) {
			throw new NasdanikaException(e);
		}		
	}
	
	/**
	 * Computes site map tree script - context property computer
	 * @param context
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected String computeSiteMapTreeScript(
			Context context, 
			Action action, 
			BiFunction<Label, URI, URI> uriResolver,
			Map<EObject, Action> registry,
			ProgressMonitor progressMonitor) {
		// TODO - actions from action prototype, e.g. Ecore doc actions, to the tree.
		
		JsTreeFactory jsTreeFactory = context.get(JsTreeFactory.class, JsTreeFactory.INSTANCE);
		Map<EObject, JsTreeNode> nodeMap = new HashMap<>();
		for (Entry<EObject, Action> re: registry.entrySet()) {
			Action treeAction = re.getValue();
			
			Link link = AppFactory.eINSTANCE.createLink();
			String treeActionText = treeAction.getText();
			int maxLength = 50;
			link.setText(treeActionText.length() > maxLength ? treeActionText.substring(0, maxLength) + "..." : treeActionText);
			link.setIcon(treeAction.getIcon());
			
			URI bURI = uriResolver.apply(action, (URI) null);
			URI tURI = uriResolver.apply(treeAction, bURI);
			if (tURI != null) {
				link.setLocation(tURI.toString());
			}
			LinkJsTreeNodeSupplierFactoryAdapter<Link> adapter = new LinkJsTreeNodeSupplierFactoryAdapter<>(link);
			
			try {
				JsTreeNode jsTreeNode = adapter.create(context).execute(progressMonitor);
				jsTreeNode.attribute(Util.DATA_NSD_ACTION_UUID_ATTRIBUTE, treeAction.getUuid());
				nodeMap.put(re.getKey(), jsTreeNode);
			} catch (Exception e) {
				throw new NasdanikaException(e);
			}
		}
		
		Map<EObject, JsTreeNode> roots = new HashMap<>(nodeMap);
		
		Map<EObject,Map<String,List<JsTreeNode>>> refMap = new HashMap<>();
		for (EObject eObj: new ArrayList<>(nodeMap.keySet())) {
			Map<String,List<JsTreeNode>> rMap = new TreeMap<>();					
			for (EReference eRef: eObj.eClass().getEAllReferences()) {
				if (eRef.isContainment()) {
					Object eRefValue = eObj.eGet(eRef);
					List<JsTreeNode> refNodes = new ArrayList<>();
					for (Object ve: eRefValue instanceof Collection ? (Collection<Object>) eRefValue : Collections.singletonList(eRefValue)) {
						JsTreeNode refNode = roots.remove(ve);
						if (refNode != null) {
							refNodes.add(refNode);
						}
					}
					if (!refNodes.isEmpty()) {
						rMap.put(org.nasdanika.common.Util.nameToLabel(eRef.getName()) , refNodes);
					}
				}
			}
			if (!rMap.isEmpty()) {
				refMap.put(eObj, rMap);
			}
		}
		
		for (Entry<EObject, JsTreeNode> ne: nodeMap.entrySet()) {
			Map<String, List<JsTreeNode>> refs = refMap.get(ne.getKey());
			if (refs != null) {
				for (Entry<String, List<JsTreeNode>> ref: refs.entrySet()) {
					JsTreeNode refNode = jsTreeFactory.jsTreeNode();
					refNode.text(ref.getKey());
					refNode.children().addAll(ref.getValue());
					ne.getValue().children().add(refNode);
				}
			}
		}
		
		JSONObject jsTree = jsTreeFactory.buildJsTree(roots.values());

		List<String> plugins = new ArrayList<>();
		plugins.add("state");
		plugins.add("search");
		JSONObject searchConfig = new JSONObject();
		searchConfig.put("show_only_matches", true);
		jsTree.put("search", searchConfig);
		jsTree.put("plugins", plugins); 		
		jsTree.put("state", Collections.singletonMap("key", "nsd-site-map-tree"));
		
		// Deletes selection from state
		String filter = NavigationPanelConsumerFactoryAdapter.CLEAR_STATE_FILTER + " tree.search.search_callback = (results, node) => results.split(' ').includes(node.original['data-nsd-action-uuid']);";
		
		return jsTreeFactory.bind("#nsd-site-map-tree", jsTree, filter, null).toString();					
	}
	
	/**
	 * Computes a table of contents for a Drawio element
	 * @param element
	 * @param context
	 * @return
	 */
	protected Object computeTableOfContents(org.nasdanika.drawio.Element element, Context context) {
		HTMLFactory htmlFactory = context.get(HTMLFactory.class, HTMLFactory.INSTANCE);
		if (element instanceof org.nasdanika.drawio.Document) {
			List<Page> pages = ((org.nasdanika.drawio.Document) element).getPages();
			if (pages.size() == 1) {
				return computeTableOfContents(pages.get(0), context);
			}
			Tag ol = htmlFactory.tag(TagName.ol);
			for (Page page: pages) {
				Tag li = htmlFactory.tag(TagName.li, page.getName(), computeTableOfContents(page, context));
				ol.content(li);
			}
			return ol;
		}
		
		if (element instanceof Page) {
			List<Layer> layers = new ArrayList<>(((Page) element).getModel().getRoot().getLayers());
			if (layers.size() == 1) {
				return computeTableOfContents(layers.get(0), context);
			}
			Collections.reverse(layers);
			Tag ol = htmlFactory.tag(TagName.ol);
			for (Layer layer: layers) {
				if (org.nasdanika.common.Util.isBlank(layer.getLabel())) {
					ol.content(computeTableOfContents(layer, context));
				} else {
					Tag li = htmlFactory.tag(
							TagName.li, 
							org.nasdanika.common.Util.isBlank(layer.getLink()) || layer.getLinkedPage() != null ? layer.getLabel() : htmlFactory.tag(TagName.a, layer.getLabel()).attribute("href", layer.getLink()),
							org.nasdanika.common.Util.isBlank(layer.getTooltip()) ? "" : " - " + Jsoup.parse(layer.getTooltip()).text() ,
							computeTableOfContents(layer, context));
					ol.content(li);								
				}							
			}
			return ol;
		}
		
		if (element instanceof Layer) { // Including nodes
			List<LayerElement> layerElements = new ArrayList<>(((Layer) element).getElements());
			Collections.sort(layerElements, new LabelModelElementComparator(false));
			if (element instanceof org.nasdanika.drawio.Node) {
				List<LayerElement> outgoingConnections = new ArrayList<>(((org.nasdanika.drawio.Node) element).getOutgoingConnections());
				Collections.sort(outgoingConnections, new LabelModelElementComparator(false));
				layerElements.addAll(outgoingConnections);
			}
			
			Tag ol = htmlFactory.tag(TagName.ol);
			for (LayerElement layerElement: layerElements) {
				if (layerElement instanceof org.nasdanika.drawio.Node || (layerElement instanceof Connection && (((Connection) layerElement).getSource() == null || ((Connection) layerElement).getSource() == element))) {
					if (org.nasdanika.common.Util.isBlank(layerElement.getLabel())) { 
						ol.content(computeTableOfContents(layerElement, context));
					} else {
						Tag li = htmlFactory.tag(
								TagName.li,
								org.nasdanika.common.Util.isBlank(layerElement.getLink()) || layerElement.getLinkedPage() != null ? Jsoup.parse(layerElement.getLabel()).text() : htmlFactory.tag(TagName.a, Jsoup.parse(layerElement.getLabel()).text()).attribute("href", layerElement.getLink()),										
								org.nasdanika.common.Util.isBlank(layerElement.getTooltip()) ? "" : " - " + Jsoup.parse(layerElement.getTooltip()).text() ,
										computeTableOfContents(layerElement, context));
						ol.content(li);								
					}
				}
			}
			return ol;						
		}
		
		return null; 		
	}
	
	protected String computeSemanticLink(
			Context context, 
			String key, 
			String path, 
			Action action,
			URI baseSemanticURI,
			BiFunction<Label, URI, URI> uriResolver,
			Map<EObject, Action> registry) {
		int spaceIdx = path.indexOf(' ');
		URI targetURI = URI.createURI(spaceIdx == -1 ? path : path.substring(0, spaceIdx));
		if (baseSemanticURI != null && targetURI.isRelative()) {
			targetURI = targetURI.resolve(baseSemanticURI.appendSegment(""));
		}	
		URI bURI = uriResolver.apply(action, (URI) null);						
		for (Entry<EObject, Action> registryEntry: registry.entrySet()) {
			for (URI semanticURI: NcoreUtil.getUris(registryEntry.getKey())) {
				if (Objects.equals(targetURI, semanticURI)) {
					Action targetAction = registryEntry.getValue();
					HTMLFactory htmlFactory = context.get(HTMLFactory.class, HTMLFactory.INSTANCE);
					URI targetActionURI = uriResolver.apply(targetAction, bURI);
					Tag tag = htmlFactory.tag(targetActionURI == null ? TagName.span : TagName.a, spaceIdx == -1 ? targetAction.getText() : path.substring(spaceIdx + 1));
					String targetActionTooltip = targetAction.getTooltip();
					if (!org.nasdanika.common.Util.isBlank(targetActionTooltip)) {
						tag.attribute("title", targetActionTooltip);
					}
					if (targetActionURI != null) {
						tag.attribute("href", targetActionURI.toString());
					}
					return tag.toString(); 
				}
			}
		}
		return null;
	}
	
	protected String computeSemanticReferfence(
			Context context, 
			String key, 
			String path,
			Action action,
			URI baseSemanticURI,
			BiFunction<Label, URI, URI> uriResolver,
			Map<EObject, Action> registry) {

		URI targetURI = URI.createURI(path);
		if (baseSemanticURI != null && targetURI.isRelative()) {
			targetURI = targetURI.resolve(baseSemanticURI.appendSegment(""));
		}	
		URI bURI = uriResolver.apply(action, (URI) null);						
		for (Entry<EObject, Action> registryEntry: registry.entrySet()) {
			for (URI semanticURI: NcoreUtil.getUris(registryEntry.getKey())) {
				if (Objects.equals(targetURI, semanticURI)) {
					Action targetAction = registryEntry.getValue();
					URI targetActionURI = uriResolver.apply(targetAction, bURI);
					if (targetActionURI != null) {
						return targetActionURI.toString();
					}
				}
			}
		}
		return null;
	}
		
	/**
	 * {@link ActionContentProvider} method
	 * @param action
	 * @param uriResolver
	 * @param progressMonitor
	 * @return
	 */
	protected EList<EObject> getActionContent(
			Action action, 
			BiFunction<Label, URI, URI> uriResolver,
			Map<EObject, Action> registry,
			File resourceWorkDir,
			Context context,
			java.util.function.Consumer<Diagnostic> diagnosticConsumer,
			ProgressMonitor progressMonitor) {

		List<Object> contentContributions = new ArrayList<>();
		
		Context actionContentContext = createActionContentContext(action, uriResolver, registry, (ContentConsumer) contentContributions::add, context, progressMonitor);
		
		File contentDir = new File(resourceWorkDir, "content");
		contentDir.mkdirs();
		
		String fileName = action.getUuid() + ".html";
		SupplierFactory<InputStream> contentFactory = org.nasdanika.common.Util.asInputStreamSupplierFactory(action.getContent());			
		try (InputStream contentStream = org.nasdanika.common.Util.call(contentFactory.create(actionContentContext), progressMonitor, diagnosticConsumer, Status.FAIL, Status.ERROR)) {
			if (contentContributions.isEmpty()) {
				Files.copy(contentStream, new File(contentDir, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
			} else {
				Stream<InputStream> pageBodyContributionsStream = contentContributions.stream().filter(Objects::nonNull).map(e -> {
					try {
						return DefaultConverter.INSTANCE.toInputStream(e.toString());
					} catch (IOException ex) {
						throw new NasdanikaException("Error converting element to InputStream: " + ex, ex);
					}
				});
				Stream<InputStream> concatenatedStream = Stream.concat(pageBodyContributionsStream, Stream.of(contentStream));
				Files.copy(org.nasdanika.common.Util.join(concatenatedStream), new File(contentDir, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			throw new NasdanikaException(e);
		}
		
		org.nasdanika.exec.content.Resource contentResource = ContentFactory.eINSTANCE.createResource();
		contentResource.setLocation("../content/" + fileName);
		progressMonitor.worked(1, "[Action content] " + action.getText() + " -> " + fileName);
		return ECollections.singletonEList(contentResource);					
	}

	/**
	 * Registers semantic-link and semantic-ref property computers
	 * @param action
	 * @param uriResolver
	 * @param registry
	 * @param mctx
	 */
	protected Context createActionContentContext(
			Action action, 
			BiFunction<Label, URI, URI> uriResolver,
			Map<EObject, Action> registry,
			ContentConsumer contentConsumer,
			Context context,
			ProgressMonitor progressMonitor) {
		
		MutableContext mctx = context.fork();
		mctx.put("nsd-site-map-tree-script", (Function<Context, String>) ctx -> computeSiteMapTreeScript(ctx, action, uriResolver, registry, progressMonitor));
		
		if (contentConsumer != null) {
			mctx.register(ContentConsumer.class, contentConsumer);
		}
		
		Map<String, org.nasdanika.drawio.Document> representations = NcoreActionBuilder.resolveRepresentationLinks(action, uriResolver, progressMonitor);
		for (Entry<String, org.nasdanika.drawio.Document> representationEntry: representations.entrySet()) {
			try {
				mctx.put("representations/" + representationEntry.getKey() + "/diagram", representationEntry.getValue().save(true));
				Object toc = computeTableOfContents(representationEntry.getValue(), mctx);
				if (toc != null) {
					mctx.put("representations/" + representationEntry.getKey() + "/toc", toc.toString());
				}
			} catch (TransformerException | IOException e) {
				throw new NasdanikaException("Error saving document");
			}
		}
		
		Optional<URI> baseSemanticURI = registry
				.entrySet()
				.stream()
				.filter(e -> Objects.equals(e.getValue().getUuid(), action.getUuid()))
				.flatMap(e -> NcoreUtil.getUris(e.getKey()).stream())
				.filter(Objects::nonNull)
				.filter(u -> !u.isRelative() && u.isHierarchical())
				.findFirst();									
		
		PropertyComputer semanticLinkPropertyComputer = new PropertyComputer() {
			
			@SuppressWarnings("unchecked")
			@Override
			public <T> T compute(Context ctx, String key, String path, Class<T> type) {
				if (type == null || type.isAssignableFrom(String.class)) {
					return (T) computeSemanticLink(ctx, key, path, action, baseSemanticURI.orElse(null), uriResolver, registry);			
				}
				return null;
			}
		}; 
		
		mctx.put("semantic-link", semanticLinkPropertyComputer);
					
		PropertyComputer semanticReferencePropertyComputer = new PropertyComputer() {
			
			@SuppressWarnings("unchecked")
			@Override
			public <T> T compute(Context propertyComputerContext, String key, String path, Class<T> type) {
				if (type == null || type.isAssignableFrom(String.class)) {
					return (T) computeSemanticReferfence(propertyComputerContext, key, path, action, baseSemanticURI.orElse(null), uriResolver, registry);
				}
				return null;
			}
		};
		
		mctx.put("semantic-ref", semanticReferencePropertyComputer);
		
		return mctx;
	}	
	
	/**
	 * Generates files from the previously generated resource model.
	 * @throws org.eclipse.emf.common.util.DiagnosticException 
	 * @throws IOException 
	 * @throws Exception
	 */
	public Map<String, Collection<String>> generateContainer(
			Resource resourceModel,
			File workDir,
			File outputDir,
			Predicate<String> cleanPredicate,
			String siteMapDomain,
			String containerName, 
			Context context, 
			ProgressMonitor progressMonitor) throws org.eclipse.emf.common.util.DiagnosticException, IOException {
		Map<String, Collection<String>> errors = new TreeMap<>();
		
		Util.generateContainer(
				resourceModel, 
				new FileSystemContainer(workDir), 
				context, 
				progressMonitor);
		
		org.nasdanika.common.Util.copy(new File(workDir, containerName), outputDir, true, cleanPredicate, null);
		
		Util.generateSitemapAndSearch(
				outputDir, 
				siteMapDomain, 
				this::isSiteMap, 
				getChangeFrequency(), 
				this::isSearch, 
				(path, error) ->  {
					progressMonitor.worked(Status.ERROR, 1, "[" + path +"] " + error);
					errors.computeIfAbsent(path, p -> new ArrayList<>()).add(error);
				});		
		
		return errors;
	}
	
	protected ChangeFreq getChangeFrequency() {
		return ChangeFreq.WEEKLY;
	}
	
	protected boolean isSiteMap(File file, String path) {
		return path.endsWith(".html");
	}
	
	protected boolean isSearch(File file, String path) {
		return path.endsWith(".html") && !"search.html".equals(path);
	}
	
	public void generate() throws IOException, org.eclipse.emf.common.util.DiagnosticException  {
		URI semanticModelURI = URI.createFileURI(new File("model/high-level-architecture.drawio").getAbsolutePath());
		
		String rootActionResource = "model/root-action.yml";
		URI rootActionURI = URI.createFileURI(new File(rootActionResource).getAbsolutePath());//.appendFragment("/");
		
		String pageTemplateResource = "model/page-template.yml";
		URI pageTemplateURI = URI.createFileURI(new File(pageTemplateResource).getAbsolutePath());//.appendFragment("/");
		
		String siteMapDomain = "https://docs.nasdanika.org/demo-drawio-semantic-mapping";		
		
		Map<String, Collection<String>> errors = generate(
				semanticModelURI,
				rootActionURI,
				pageTemplateURI,
				siteMapDomain,
				new File("docs"),							
				null, // new File("target/model-doc"),
				false
				);
				
		if (!errors.isEmpty()) {
			throw new ExecutionException("There are problems with pages: " + errors);
		};
		
	}	
	
	public Map<String, Collection<String>> generate(
		URI semanticModelURI,
		URI rootActionURI,
		URI pageTemplateURI,
		String siteMapDomain,
		File outputDir,
		File workDir,
		boolean cleanWorkDir) throws IOException, org.eclipse.emf.common.util.DiagnosticException  {
		
		String modelName = semanticModelURI.lastSegment();
		if (org.nasdanika.common.Util.isBlank(modelName)) {
			modelName = "semantic-model";
		}
		if (workDir == null) {
			workDir = Files.createTempDirectory(modelName).toFile();
			cleanWorkDir = true;
		}
		
		if (!workDir.isDirectory()) {
			if (!workDir.mkdirs()) {
				throw new IOException("Cannot create a working directory: " + workDir.getAbsolutePath());
			}
		}
		
		try {
			File modelsDir = new File(workDir, "models");
			File actionModelsDir = new File(workDir, "actions");
			File resourceModelsDir = new File(workDir, "resources");
			File siteWorkDir = new File(workDir, "site");			
			
			org.nasdanika.common.Util.delete(modelsDir);
			org.nasdanika.common.Util.delete(actionModelsDir);
			org.nasdanika.common.Util.delete(resourceModelsDir);
			org.nasdanika.common.Util.delete(siteWorkDir);
			
			modelsDir.mkdirs();
			actionModelsDir.mkdirs();
			resourceModelsDir.mkdirs();
			
			MutableContext context = Context.EMPTY_CONTEXT.fork();		
			context.put("model-name", modelName);
	
			try (ProgressMonitor progressMonitor = new PrintStreamProgressMonitor()) {						
				URI modelsURI = URI.createFileURI(modelsDir.getAbsolutePath() + "/");							
				URI copyURI = URI.createURI(modelName + ".xml").resolve(modelsURI);		
				Resource semanticModelResource = loadSemanticModel(semanticModelURI, copyURI, context, progressMonitor.split("Loading semantic model", 1));
				
				URI actionModelsURI = URI.createFileURI(actionModelsDir.getAbsolutePath() + "/");	
				URI actionModelURI = URI.createURI(modelName + ".xml").resolve(actionModelsURI);
				BiSupplier<Resource, Map<EObject, Action>> rootActionAndRegistry = generateActionModel(semanticModelResource, actionModelURI, context, progressMonitor.split("Generating action model", 1));
				
				URI resourceModelsURI = URI.createFileURI(resourceModelsDir.getAbsolutePath() + "/");	
				URI resourceURI = URI.createURI(modelName + ".xml").resolve(resourceModelsURI);
				Resource resourceModel = generateResourceModel(
						rootActionAndRegistry.getFirst(), 
						rootActionAndRegistry.getSecond(), 
						rootActionURI,
						pageTemplateURI,
						resourceURI, 
						modelName, 
						resourceModelsDir,
						context, 
						progressMonitor.split("Generating resource model", 1));
				
	
				// Cleanup docs, keep CNAME, favicon.ico, and images directory. Copy from target/model-doc/site/nasdanika
				Predicate<String> cleanPredicate = path -> {
					return !"CNAME".equals(path) && !"favicon.ico".equals(path) && !path.startsWith("images/");
				};
				
				
				return generateContainer(
						resourceModel,
						siteWorkDir,
						outputDir,
						cleanPredicate,
						siteMapDomain,
						modelName, 
						context, 
						progressMonitor.split("Generating container", 1));
			}
		} finally {
			if (cleanWorkDir) {
				org.nasdanika.common.Util.delete(workDir);
			}
		}
	}

}
