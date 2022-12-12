package org.nasdanika.demo.drawio.semanticmapping;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.xml.transform.TransformerException;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.nasdanika.common.ConsumerFactory;
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
import org.nasdanika.common.Supplier;
import org.nasdanika.common.SupplierFactory;
import org.nasdanika.drawio.Layer;
import org.nasdanika.drawio.LayerElement;
import org.nasdanika.drawio.Page;
import org.nasdanika.drawio.comparators.LabelModelElementComparator;
import org.nasdanika.emf.EObjectAdaptable;
import org.nasdanika.emf.EmfUtil;
import org.nasdanika.emf.persistence.EObjectLoader;
import org.nasdanika.emf.persistence.GitMarkerFactory;
import org.nasdanika.emf.persistence.MarkerFactory;
import org.nasdanika.emf.persistence.NcoreDrawioResourceFactory;
import org.nasdanika.emf.persistence.NcoreObjectLoaderSupplier;
import org.nasdanika.exec.ExecPackage;
import org.nasdanika.exec.content.ContentFactory;
import org.nasdanika.exec.content.ContentPackage;
import org.nasdanika.exec.resources.Container;
import org.nasdanika.exec.resources.ReconcileAction;
import org.nasdanika.exec.resources.ResourcesFactory;
import org.nasdanika.exec.resources.ResourcesPackage;
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
import org.nasdanika.html.model.app.AppPackage;
import org.nasdanika.html.model.app.Label;
import org.nasdanika.html.model.app.Link;
import org.nasdanika.html.model.app.gen.ActionContentProvider;
import org.nasdanika.html.model.app.gen.AppAdapterFactory;
import org.nasdanika.html.model.app.gen.LinkJsTreeNodeSupplierFactoryAdapter;
import org.nasdanika.html.model.app.gen.NavigationPanelConsumerFactoryAdapter;
import org.nasdanika.html.model.app.gen.PageContentProvider;
import org.nasdanika.html.model.app.gen.Util;
import org.nasdanika.html.model.app.util.ActionProvider;
import org.nasdanika.html.model.app.util.AppObjectLoaderSupplier;
import org.nasdanika.html.model.bootstrap.BootstrapPackage;
import org.nasdanika.html.model.html.HtmlPackage;
import org.nasdanika.html.model.html.gen.ContentConsumer;
import org.nasdanika.ncore.NcorePackage;
import org.nasdanika.ncore.util.NcoreResourceSet;
import org.nasdanika.ncore.util.NcoreUtil;
import org.nasdanika.persistence.ObjectLoaderResourceFactory;
import org.nasdanika.resources.BinaryEntityContainer;
import org.nasdanika.resources.FileSystemContainer;

import com.redfin.sitemapgenerator.ChangeFreq;
import com.redfin.sitemapgenerator.WebSitemapGenerator;
import com.redfin.sitemapgenerator.WebSitemapUrl;

public class DrawioSemanticMappingGenerator {

	private static final File GENERATED_MODELS_BASE_DIR = new File("target/model-doc");
	private static final File MODELS_DIR = new File(GENERATED_MODELS_BASE_DIR, "models");
	private static final File ACTION_MODELS_DIR = new File(GENERATED_MODELS_BASE_DIR, "actions");
	private static final File RESOURCE_MODELS_DIR = new File(GENERATED_MODELS_BASE_DIR, "resources");
	
	private static final URI MODELS_URI = URI.createFileURI(MODELS_DIR.getAbsolutePath() + "/");	
	private static final URI ACTION_MODELS_URI = URI.createFileURI(ACTION_MODELS_DIR.getAbsolutePath() + "/");	
	private static final URI RESOURCE_MODELS_URI = URI.createFileURI(RESOURCE_MODELS_DIR.getAbsolutePath() + "/");	
	
	/**
	 * Loads a model from YAML, creates a copy and stores to XMI.
	 * @param name
	 * @param progressMonitor
	 * @throws Exception
	 */
	protected void loadSemanticModel(String name, Context context, ProgressMonitor progressMonitor) throws Exception {
		URI resourceURI = URI.createFileURI(new File("model/" + name).getAbsolutePath());
		
		ResourceSet rSet = createResourceSet(context, progressMonitor);
		
		Supplier<EObject> ncoreObjectLoaderSupplier = new NcoreObjectLoaderSupplier(resourceURI, context) {
			
			@Override
			protected ResourceSet createResourceSet(ProgressMonitor progressMonitor) {
				return rSet;
			}
			
		};
		
		org.nasdanika.common.Consumer<EObject> modelConsumer = new org.nasdanika.common.Consumer<EObject>() {

			@Override
			public double size() {
				return 1;
			}

			@Override
			public String name() {
				return "Saving loaded model";
			}

			@Override
			public void execute(EObject obj, ProgressMonitor progressMonitor) {
				EObject copy = EcoreUtil.copy(obj);
				ResourceSet resourceSet = new NcoreResourceSet();
				resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(org.eclipse.emf.ecore.resource.Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
				
				org.eclipse.emf.ecore.resource.Resource instanceModelResource = resourceSet.createResource(URI.createURI(name + ".xml").resolve(MODELS_URI));
				instanceModelResource.getContents().add(copy);
				
				org.eclipse.emf.common.util.Diagnostic copyDiagnostic = org.nasdanika.emf.EmfUtil.resolveClearCacheAndDiagnose(resourceSet, context);
				int severity = copyDiagnostic.getSeverity();
				if (severity != org.eclipse.emf.common.util.Diagnostic.OK) {
					EmfUtil.dumpDiagnostic(copyDiagnostic, 2, System.err);
					throw new ExecutionException(new org.eclipse.emf.common.util.DiagnosticException(copyDiagnostic));
				}
				try {
					instanceModelResource.save(null);
				} catch (IOException e) {
					throw new ExecutionException(e, this);
				}
			}
			
		};
		
		try {
			org.nasdanika.common.Diagnostic diagnostic = org.nasdanika.common.Util.call(ncoreObjectLoaderSupplier.then(modelConsumer), progressMonitor);
			if (diagnostic.getStatus() == Status.FAIL || diagnostic.getStatus() == Status.ERROR) {
				System.err.println("***********************");
				System.err.println("*      Diagnostic     *");
				System.err.println("***********************");
				diagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);
			}
			if (diagnostic.getStatus() != Status.SUCCESS) {
				throw new DiagnosticException(diagnostic);
			}
			
			if (diagnostic.getStatus() == Status.WARNING || diagnostic.getStatus() == Status.ERROR) {
				System.err.println("***********************");
				System.err.println("*      Diagnostic     *");
				System.err.println("***********************");
				diagnostic.dump(System.err, 4, Status.ERROR, Status.WARNING);
			}
		} catch (DiagnosticException e) {
			System.err.println("******************************");
			System.err.println("*      Diagnostic failed     *");
			System.err.println("******************************");
			e.getDiagnostic().dump(System.err, 4, Status.FAIL);
			throw e;
		}
	}
		
	public static void copy(File source, File target, boolean cleanTarget, BiConsumer<File,File> listener) throws IOException {
		if (cleanTarget && target.isDirectory()) {
			delete(target.listFiles());
		}
		if (source.isDirectory()) {
			target.mkdirs();
			for (File sc: source.listFiles()) {
				copy(sc, new File(target, sc.getName()), false, listener);
			}
		} else if (source.isFile()) {
			Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);			
			if (listener != null) {
				listener.accept(source, target);
			}
		}
	}
	
	private static void delete(File... files) {
		for (File file: files) {
			if (file.exists()) {
				if (file.isDirectory()) {
					delete(file.listFiles());
				}
				file.delete();
			}
		}
	}
		
	private static void copy(File source, File target, boolean cleanTarget, Predicate<String> cleanPredicate, BiConsumer<File,File> listener) throws IOException {
		if (cleanTarget && target.isDirectory()) {
			delete(null, cleanPredicate, target.listFiles());
		}
		if (source.isDirectory()) {
			target.mkdirs();
			for (File sc: source.listFiles()) {
				copy(sc, new File(target, sc.getName()), false, listener);
			}
		} else if (source.isFile()) {
			Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);			
			if (listener != null) {
				listener.accept(source, target);
			}
		}
	}
	
	private static void delete(String path, Predicate<String> deletePredicate, File... files) {
		for (File file: files) {
			String filePath = path == null ? file.getName() : path + "/" + file.getName();
			if (file.exists() && (deletePredicate == null || deletePredicate.test(filePath))) {
				if (file.isDirectory()) {
					delete(filePath, deletePredicate, file.listFiles());
				}
				file.delete();
			}
		}
	}	
		
	/**
	 * Loads instance model from previously generated XMI, diagnoses, generates action model.
	 * @throws Exception
	 */
	public Map<EObject,Action>  generateActionModel(String name, Context context, ProgressMonitor progressMonitor) throws Exception {
		ResourceSet instanceModelsResourceSet = createResourceSet(context, progressMonitor);
		Resource instanceModelResource = instanceModelsResourceSet.getResource(URI.createURI(name + ".xml").resolve(MODELS_URI), true);

		org.eclipse.emf.common.util.Diagnostic instanceDiagnostic = org.nasdanika.emf.EmfUtil.resolveClearCacheAndDiagnose(instanceModelsResourceSet, context);
		int severity = instanceDiagnostic.getSeverity();
		if (severity != org.eclipse.emf.common.util.Diagnostic.OK) {
			EmfUtil.dumpDiagnostic(instanceDiagnostic, 2, System.err);
			throw new org.eclipse.emf.common.util.DiagnosticException(instanceDiagnostic);
		}
		
		instanceModelsResourceSet.getAdapterFactories().add(new ActionProviderAdapterFactory(context) {
			
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
		actionModelsResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(org.eclipse.emf.ecore.resource.Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		
		org.eclipse.emf.ecore.resource.Resource actionModelResource = actionModelsResourceSet.createResource(URI.createURI(name + ".xml").resolve(ACTION_MODELS_URI));
		
		Map<EObject,Action> registry = new HashMap<>();
		EObject instance = instanceModelResource.getContents().get(0);
		Action rootAction = EObjectAdaptable.adaptTo(instance, ActionProvider.class).execute(registry::put, progressMonitor);
		Context uriResolverContext = Context.singleton(Context.BASE_URI_PROPERTY, URI.createURI("temp://" + UUID.randomUUID() + "/" + UUID.randomUUID() + "/"));
		BiFunction<Label, URI, URI> uriResolver = org.nasdanika.html.model.app.util.Util.uriResolver(rootAction, uriResolverContext);
		Adapter resolver = EcoreUtil.getExistingAdapter(rootAction, EObjectActionResolver.class);
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
		actionModelResource.getContents().add(rootAction);

		actionModelResource.save(null);
		
		return registry;
	}
	
	protected EObject loadObject(
			String resource, 
			Consumer<org.nasdanika.common.Diagnostic> diagnosticConsumer,
			Context context,
			ProgressMonitor progressMonitor) throws Exception {
		
		URI resourceURI = URI.createFileURI(new File(resource).getAbsolutePath());
				
		// Diagnosing loaded resources. 
		try {
			return Objects.requireNonNull(org.nasdanika.common.Util.call(new AppObjectLoaderSupplier(resourceURI, context), progressMonitor, diagnosticConsumer), "Loaded null from " + resource);
		} catch (DiagnosticException e) {
			System.err.println("******************************");
			System.err.println("*      Diagnostic failed     *");
			System.err.println("******************************");
			e.getDiagnostic().dump(System.err, 4, Status.FAIL);
			throw e;
		}		
	}
	
	/**
	 * Generates a resource model from an action model.
	 * @throws Exception
	 */
	public void generateResourceModel(String name, Map<EObject, Action> registry, Context context, ProgressMonitor progressMonitor) throws Exception {
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
		
		Context modelContext = Context.singleton("model-name", name);
		String actionsResource = "model/root-action.yml";
		Action root = (Action) Objects.requireNonNull(loadObject(actionsResource, diagnosticConsumer, modelContext, progressMonitor), "Loaded null from " + actionsResource);
		root.eResource().getResourceSet().getAdapterFactories().add(new AppAdapterFactory());
		
		Container container = ResourcesFactory.eINSTANCE.createContainer();
		container.setName(name);
		container.setReconcileAction(ReconcileAction.OVERWRITE);
		
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		Resource modelResource = resourceSet.createResource(URI.createURI(name + ".xml").resolve(RESOURCE_MODELS_URI));
		modelResource.getContents().add(container);
		
		String pageTemplateResource = "model/page-template.yml";
		org.nasdanika.html.model.bootstrap.Page pageTemplate = (org.nasdanika.html.model.bootstrap.Page) Objects.requireNonNull(loadObject(pageTemplateResource, diagnosticConsumer, modelContext, progressMonitor), "Loaded null from " + pageTemplateResource);
		
		File contentDir = new File(RESOURCE_MODELS_DIR, "content");
		contentDir.mkdirs();
		// Generating content file from action content 
		ActionContentProvider.Factory actionContentProviderFactory = (contentProviderContext) -> (action, uriResolver, pMonitor) -> {
			
			@SuppressWarnings("unchecked")
			java.util.function.Function<Context, String> siteMapTreeScriptComputer = ctx -> {
				// TODO - actions from action prototype, e.g. Ecore doc actions, to the tree.
				
				JsTreeFactory jsTreeFactory = contentProviderContext.get(JsTreeFactory.class, JsTreeFactory.INSTANCE);
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
						JsTreeNode jsTreeNode = adapter.create(ctx).execute(progressMonitor);
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
			};		
			
			MutableContext mctx = contentProviderContext.fork();
			mctx.put("nsd-site-map-tree-script", siteMapTreeScriptComputer);
									
			java.util.function.Function<org.nasdanika.drawio.Element,Object> tableOfContents = new java.util.function.Function<org.nasdanika.drawio.Element,Object>() {

				HTMLFactory htmlFactory = contentProviderContext.get(HTMLFactory.class, HTMLFactory.INSTANCE);

				@Override
				public Object apply(org.nasdanika.drawio.Element element) {
					if (element instanceof org.nasdanika.drawio.Document) {
						List<Page> pages = ((org.nasdanika.drawio.Document) element).getPages();
						if (pages.size() == 1) {
							return apply(pages.get(0));
						}
						Tag ol = htmlFactory.tag(TagName.ol);
						for (Page page: pages) {
							Tag li = htmlFactory.tag(TagName.li, page.getName(), apply(page));
							ol.content(li);
						}
						return ol;
					}
					
					if (element instanceof Page) {
						List<Layer> layers = new ArrayList<>(((Page) element).getModel().getRoot().getLayers());
						if (layers.size() == 1) {
							return apply(layers.get(0));
						}
						Collections.reverse(layers);
						Tag ol = htmlFactory.tag(TagName.ol);
						for (Layer layer: layers) {
							if (org.nasdanika.common.Util.isBlank(layer.getLabel())) {
								ol.content(apply(layer));
							} else {
								Tag li = htmlFactory.tag(
										TagName.li, 
										org.nasdanika.common.Util.isBlank(layer.getLink()) || layer.getLinkedPage() != null ? layer.getLabel() : htmlFactory.tag(TagName.a, layer.getLabel()).attribute("href", layer.getLink()),
										org.nasdanika.common.Util.isBlank(layer.getTooltip()) ? "" : " - " + Jsoup.parse(layer.getTooltip()).text() ,
										apply(layer));
								ol.content(li);								
							}							
						}
						return ol;
					}
					
					if (element instanceof Layer) {
						List<LayerElement> layerElements = new ArrayList<>(((Layer) element).getElements());
						Collections.sort(layerElements, new LabelModelElementComparator(false));
						Tag ol = htmlFactory.tag(TagName.ol);
						for (LayerElement layerElement: layerElements) {
							if (org.nasdanika.common.Util.isBlank(layerElement.getLabel())) {
								ol.content(apply(layerElement));
							} else {
								Tag li = htmlFactory.tag(
										TagName.li,
										org.nasdanika.common.Util.isBlank(layerElement.getLink()) || layerElement.getLinkedPage() != null ? Jsoup.parse(layerElement.getLabel()).text() : htmlFactory.tag(TagName.a, Jsoup.parse(layerElement.getLabel()).text()).attribute("href", layerElement.getLink()),										
										org.nasdanika.common.Util.isBlank(layerElement.getTooltip()) ? "" : " - " + Jsoup.parse(layerElement.getTooltip()).text() ,
										apply(layerElement));
								ol.content(li);								
							}							
						}
						return ol;
						
					}
					
					return null; 
				}
				
			};
			
			Map<String, org.nasdanika.drawio.Document> representations = NcoreActionBuilder.resolveRepresentationLinks(action, uriResolver, progressMonitor);
			for (Entry<String, org.nasdanika.drawio.Document> representationEntry: representations.entrySet()) {
				try {
					mctx.put("representations/" + representationEntry.getKey() + "/diagram", representationEntry.getValue().save(true));
					Object toc = tableOfContents.apply(representationEntry.getValue());
					if (toc != null) {
						mctx.put("representations/" + representationEntry.getKey() + "/toc", toc.toString());
					}
				} catch (TransformerException | IOException e) {
					throw new NasdanikaException("Error saving document");
				}
			}
			
			Optional<URI> baseSemanticURI = registry.entrySet().stream().filter(e -> Objects.equals(e.getValue().getUuid(), action.getUuid())).map(e -> NcoreUtil.getUri(e.getKey())).filter(u -> !u.isRelative()).findFirst();									
			
			PropertyComputer semanticLinkPropertyComputer = new PropertyComputer() {
				
				@SuppressWarnings("unchecked")
				@Override
				public <T> T compute(Context propertyComputerContext, String key, String path, Class<T> type) {
					if (type == null || type.isAssignableFrom(String.class)) {
						int spaceIdx = path.indexOf(' ');
						URI targetURI = URI.createURI(spaceIdx == -1 ? path : path.substring(0, spaceIdx));
						if (baseSemanticURI.isPresent() && targetURI.isRelative()) {
							targetURI = targetURI.resolve(baseSemanticURI.get().appendSegment(""));
						}	
						URI bURI = uriResolver.apply(action, (URI) null);						
						for (Entry<EObject, Action> registryEntry: registry.entrySet()) {
							URI semanticURI = NcoreUtil.getUri(registryEntry.getKey());
							if (Objects.equals(targetURI, semanticURI)) {
								Action targetAction = registryEntry.getValue();
								HTMLFactory htmlFactory = propertyComputerContext.get(HTMLFactory.class, HTMLFactory.INSTANCE);
								URI targetActionURI = uriResolver.apply(targetAction, bURI);
								Tag tag = htmlFactory.tag(targetActionURI == null ? TagName.span : TagName.a, spaceIdx == -1 ? targetAction.getText() : path.substring(spaceIdx + 1));
								String targetActionDescription = targetAction.getDescription();
								if (!org.nasdanika.common.Util.isBlank(targetActionDescription)) {
									tag.attribute("title", targetActionDescription);
								}
								if (targetActionURI != null) {
									tag.attribute("href", targetActionURI.toString());
								}
								return (T) tag.toString(); 
							}
						}
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
						URI targetURI = URI.createURI(path);
						if (baseSemanticURI.isPresent() && targetURI.isRelative()) {
							targetURI = targetURI.resolve(baseSemanticURI.get().appendSegment(""));
						}	
						URI bURI = uriResolver.apply(action, (URI) null);						
						for (Entry<EObject, Action> registryEntry: registry.entrySet()) {
							URI semanticURI = NcoreUtil.getUri(registryEntry.getKey());
							if (Objects.equals(targetURI, semanticURI)) {
								Action targetAction = registryEntry.getValue();
								URI targetActionURI = uriResolver.apply(targetAction, bURI);
								if (targetActionURI != null) {
									return (T) targetActionURI.toString();
								}
							}
						}
					}
					return null;
				}
			};
			
			mctx.put("semantic-ref", semanticReferencePropertyComputer);			
			
			List<Object> contentContributions = new ArrayList<>();
			mctx.register(ContentConsumer.class, (ContentConsumer) contentContributions::add);			
			
			String fileName = action.getUuid() + ".html";
			SupplierFactory<InputStream> contentFactory = org.nasdanika.common.Util.asInputStreamSupplierFactory(action.getContent());			
			try (InputStream contentStream = org.nasdanika.common.Util.call(contentFactory.create(mctx), pMonitor, diagnosticConsumer, Status.FAIL, Status.ERROR)) {
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
			System.out.println("[Action content] " + action.getName() + " -> " + fileName);
			return ECollections.singletonEList(contentResource);			
		};
		
		File pagesDir = new File(RESOURCE_MODELS_DIR, "pages");
		pagesDir.mkdirs();
		PageContentProvider.Factory pageContentProviderFactory = (contentProviderContext) -> (page, baseURI, uriResolver, pMonitor) -> {
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
				System.out.println("[Page content] " + page.getName() + " -> " + pageResource.getLocation());
				return pageResource;
			} catch (IOException e) {
				throw new NasdanikaException(e);
			}
		};
		
		Util.generateSite(
				root, 
				pageTemplate,
				container,
				actionContentProviderFactory,
				pageContentProviderFactory,
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
				System.out.println("[Page xml -> html] " + pageFile.getName());
			}
		}				
	}
	
	/**
	 * Generates files from the previously generated resource model.
	 * @throws Exception
	 */
	public void generateContainer(String name, Context context, ProgressMonitor progressMonitor) throws Exception {
		ResourceSet resourceSet = createResourceSet(context, progressMonitor);
		
		resourceSet.getAdapterFactories().add(new AppAdapterFactory());
				
		Resource containerResource = resourceSet.getResource(URI.createURI(name + ".xml").resolve(RESOURCE_MODELS_URI), true);
	
		File siteDir = new File("target/model-doc/site");
		FileSystemContainer container = new FileSystemContainer(siteDir);
		for (EObject eObject : containerResource.getContents()) {
			Diagnostician diagnostician = new Diagnostician();
			org.eclipse.emf.common.util.Diagnostic diagnostic = diagnostician.validate(eObject);
			if (diagnostic.getSeverity() == org.eclipse.emf.common.util.Diagnostic.ERROR) {
				throw new org.eclipse.emf.common.util.DiagnosticException(diagnostic);
			};
			// Diagnosing loaded resources. 
			try {
				ConsumerFactory<BinaryEntityContainer> consumerFactory = Objects.requireNonNull(EObjectAdaptable.adaptToConsumerFactory(eObject, BinaryEntityContainer.class), "Cannot adapt to ConsumerFactory");
				Diagnostic callDiagnostic = org.nasdanika.common.Util.call(consumerFactory.create(context), container, progressMonitor);
				if (callDiagnostic.getStatus() == Status.FAIL || callDiagnostic.getStatus() == Status.ERROR) {
					System.err.println("***********************");
					System.err.println("*      Diagnostic     *");
					System.err.println("***********************");
					callDiagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);
				}
				if (callDiagnostic.getStatus() != Status.SUCCESS) {
					throw new DiagnosticException(callDiagnostic);
				};
			} catch (DiagnosticException e) {
				System.err.println("******************************");
				System.err.println("*      Diagnostic failed     *");
				System.err.println("******************************");
				e.getDiagnostic().dump(System.err, 4, Status.FAIL);
				throw e;
			}
		}
		
		// Cleanup docs, keep CNAME, favicon.ico, and images directory. Copy from target/model-doc/site/nasdanika
		Predicate<String> cleanPredicate = path -> {
			return !"CNAME".equals(path) && !"favicon.ico".equals(path) && !path.startsWith("images/");
		};

		File docsDir = new File("docs");
		copy(new File(siteDir, "high-level-architecture.drawio"), docsDir, true, cleanPredicate, null);
		
		generateSitemapAndSearch(docsDir);
	}

	private void generateSitemapAndSearch(File docsDir) throws IOException {
		int[] problems = { 0 };
		
		// Site map and search index
		JSONObject searchDocuments = new JSONObject();		
		String domain = "https://docs.nasdanika.org";
		WebSitemapGenerator wsg = new WebSitemapGenerator(domain, docsDir);
		BiConsumer<File, String> listener = new BiConsumer<File, String>() {
			
			@Override
			public void accept(File file, String path) {
				if (path.endsWith(".html")) {
					try {
						WebSitemapUrl url = new WebSitemapUrl.Options(domain + "/" + path)
							    .lastMod(new Date(file.lastModified())).changeFreq(ChangeFreq.WEEKLY).build();
						wsg.addUrl(url); 
					} catch (MalformedURLException e) {
						throw new NasdanikaException(e);
					}
					
					// Excluding search.html and aggregator pages which contain information present elsewhere
					if (!"search.html".equals(path)
							&& !"all-issues.html".equals(path)
							&& !"issues.html".equals(path)
							&& !"assignments.html".equals(path)
							&& !path.endsWith("/all-issues.html")
							&& !path.endsWith("/issues.html")
							&& !path.endsWith("/assignments.html")
							&& !path.endsWith("-load-specification.html")
							&& !path.endsWith("-all-operations.html")
							&& !path.endsWith("-all-attributes.html")
							&& !path.endsWith("-all-references.html")
							&& !path.endsWith("-all-supertypes.html")) {

						try {
							Predicate<String> predicate = org.nasdanika.html.model.app.gen.Util.createRelativeLinkPredicate(file, docsDir);						
							Consumer<? super Element> inspector = org.nasdanika.html.model.app.gen.Util.createInspector(predicate, error -> {
								System.err.println("[" + path +"] " + error);
								++problems[0];
							});
							
							JSONObject searchDocument = org.nasdanika.html.model.app.gen.Util.createSearchDocument(path, file, inspector, DrawioSemanticMappingGenerator.this::configureSearch);
							if (searchDocument != null) {
								searchDocuments.put(path, searchDocument);
							}
						} catch (IOException e) {
							throw new NasdanikaException(e);
						}
					}
				}
			}
		};
		org.nasdanika.common.Util.walk(null, listener, docsDir.listFiles());
		wsg.write();	

		try (FileWriter writer = new FileWriter(new File(docsDir, "search-documents.js"))) {
			writer.write("var searchDocuments = " + searchDocuments);
		}
		
		if (problems[0] != 0) {
			throw new ExecutionException("There are problems with pages: " + problems[0]);
		};
	}
	
	protected boolean configureSearch(String path, Document doc) {
		Element head = doc.head();
		URI base = URI.createURI("temp://" + UUID.randomUUID() + "/");
		URI searchScriptURI = URI.createURI("search-documents.js").resolve(base);
		URI thisURI = URI.createURI(path).resolve(base);
		URI relativeSearchScriptURI = searchScriptURI.deresolve(thisURI, true, true, true);
		head.append(System.lineSeparator() + "<script src=\"" + relativeSearchScriptURI + "\"></script>" + System.lineSeparator());
		head.append(System.lineSeparator() + "<script src=\"https://unpkg.com/lunr/lunr.js\"></script>" + System.lineSeparator());
				
		try (InputStream in = new FileInputStream("model/search.js")) {
			head.append(System.lineSeparator() + "<script>" + System.lineSeparator() + DefaultConverter.INSTANCE.toString(in) + System.lineSeparator() + "</script>" + System.lineSeparator());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return true;
	}
		
	private static ResourceSet createResourceSet(Context context, ProgressMonitor progressMonitor) {
		// Load model from XMI
		ResourceSet resourceSet = new NcoreResourceSet();
		
		EObjectLoader eObjectLoader = new EObjectLoader(null, null, resourceSet);
		GitMarkerFactory markerFactory = new GitMarkerFactory();
		eObjectLoader.setMarkerFactory(markerFactory);
		Resource.Factory objectLoaderResourceFactory = new ObjectLoaderResourceFactory() {
			
			@Override
			protected org.nasdanika.persistence.ObjectLoader getObjectLoader(Resource resource) {
				return eObjectLoader;
			}
			
		};
		Map<String, Object> extensionToFactoryMap = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
		extensionToFactoryMap.put("yml", objectLoaderResourceFactory);
		extensionToFactoryMap.put("json", objectLoaderResourceFactory);
		resourceSet.getResourceFactoryRegistry().getProtocolToFactoryMap().put("data", objectLoaderResourceFactory);
		
		NcoreDrawioResourceFactory<EObject> ncoreDrawioResourceFactory = new NcoreDrawioResourceFactory<EObject>() {
			
			@Override
			protected ResourceSet getResourceSet() {
				return resourceSet;
			}
			
			@Override
			protected ProgressMonitor getProgressMonitor(URI uri) {
				return progressMonitor;
			}
			
			@Override
			protected MarkerFactory getMarkerFactory() {
				return markerFactory;
			}
			
		};
		
		extensionToFactoryMap.put("drawio", ncoreDrawioResourceFactory);		
		extensionToFactoryMap.put(org.eclipse.emf.ecore.resource.Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		
		resourceSet.getPackageRegistry().put(NcorePackage.eNS_URI, NcorePackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ExecPackage.eNS_URI, ExecPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ContentPackage.eNS_URI, ContentPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ResourcesPackage.eNS_URI, ResourcesPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(HtmlPackage.eNS_URI, HtmlPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(BootstrapPackage.eNS_URI, BootstrapPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(AppPackage.eNS_URI, AppPackage.eINSTANCE);
		
		resourceSet.getAdapterFactories().add(new AppAdapterFactory());
		
		return resourceSet;
	}
	
	public void generate() throws Exception {
		delete(MODELS_DIR);
		delete(ACTION_MODELS_DIR);
		delete(RESOURCE_MODELS_DIR);
		
		MODELS_DIR.mkdirs();
		ACTION_MODELS_DIR.mkdirs();
		RESOURCE_MODELS_DIR.mkdirs();

		ProgressMonitor progressMonitor = new PrintStreamProgressMonitor();		
		MutableContext context = Context.EMPTY_CONTEXT.fork();		
		generateSite("high-level-architecture.drawio", context, progressMonitor);
	}
	
	private void generateSite(String name, Context context, ProgressMonitor progressMonitor) throws Exception {
		System.out.println("Generating site: " + name);
		
		long start = System.currentTimeMillis();
		loadSemanticModel(name, context, progressMonitor);
		System.out.println("\tGenerated instance model in " + (System.currentTimeMillis() - start) + " milliseconds");
		start = System.currentTimeMillis();
		
		Map<EObject, Action> registry = generateActionModel(name, context, progressMonitor);
		System.out.println("\tGenerated action model in " + (System.currentTimeMillis() - start) + " milliseconds");
		start = System.currentTimeMillis();
		
		generateResourceModel(name, registry, context, progressMonitor);
		System.out.println("\tGenerated resource model in " + (System.currentTimeMillis() - start) + " milliseconds");
		start = System.currentTimeMillis();
		
		generateContainer(name, context, progressMonitor);
		System.out.println("\tGenerated site in " + (System.currentTimeMillis() - start) + " milliseconds");
	}

}
