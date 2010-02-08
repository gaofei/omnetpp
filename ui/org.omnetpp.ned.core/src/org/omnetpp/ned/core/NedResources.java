/*--------------------------------------------------------------*
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.ned.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.internal.events.ResourceDelta;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.omnetpp.common.Debug;
import org.omnetpp.common.markers.ProblemMarkerSynchronizer;
import org.omnetpp.common.project.ProjectUtils;
import org.omnetpp.common.util.DelayedJob;
import org.omnetpp.common.util.DisplayUtils;
import org.omnetpp.common.util.StringUtils;
import org.omnetpp.ned.engine.NEDParser;
import org.omnetpp.ned.model.INedElement;
import org.omnetpp.ned.model.INedErrorStore;
import org.omnetpp.ned.model.NedElement;
import org.omnetpp.ned.model.NedTreeDifferenceUtils;
import org.omnetpp.ned.model.NedTreeUtil;
import org.omnetpp.ned.model.SysoutNedErrorStore;
import org.omnetpp.ned.model.ex.CompoundModuleElementEx;
import org.omnetpp.ned.model.ex.NedElementUtilEx;
import org.omnetpp.ned.model.ex.NedFileElementEx;
import org.omnetpp.ned.model.ex.PropertyElementEx;
import org.omnetpp.ned.model.interfaces.INedTypeInfo;
import org.omnetpp.ned.model.interfaces.INedTypeElement;
import org.omnetpp.ned.model.interfaces.INedTypeLookupContext;
import org.omnetpp.ned.model.notification.INedChangeListener;
import org.omnetpp.ned.model.notification.NedBeginModelChangeEvent;
import org.omnetpp.ned.model.notification.NedChangeListenerList;
import org.omnetpp.ned.model.notification.NedEndModelChangeEvent;
import org.omnetpp.ned.model.notification.NedFileRemovedEvent;
import org.omnetpp.ned.model.notification.NedModelChangeEvent;
import org.omnetpp.ned.model.notification.NedModelEvent;
import org.omnetpp.ned.model.notification.NedStructuralChangeEvent;
import org.omnetpp.ned.model.pojo.NedElementTags;

/**
 * Parses all NED files in the workspace and makes them available for other
 * plugins for consistency checks among NED files etc.
 *
 * It listens to workspace resource changes and modifies its content based on
 * change notifications.
 *
 * @author andras
 */
//XXX is "element" argument to NedBeginModelChangeEvent useful...? we don't use it in editors/views
//XXX remove "source" from plain NedModelChangeEvent too (and turn "anything might have changed" event into a separate class)
@SuppressWarnings("restriction")
public class NedResources implements INedResources, IResourceChangeListener {

    private boolean debug = true;

    // singleton instance
    private static NedResources instance = null;
    // list of objects that listen on *all* NED changes
    private NedChangeListenerList nedModelChangeListenerList = null;

    // associate IFiles with their NedElement trees
    private final Map<IFile, NedFileElementEx> nedFiles = new HashMap<IFile, NedFileElementEx>();
    private final Map<NedFileElementEx, IFile> nedElementFiles = new HashMap<NedFileElementEx,IFile>();

    // number of the editors connected to a given NED file
    private final Map<IFile, Integer> connectCount = new HashMap<IFile, Integer>();

    static class ProjectData {
        // NED Source Folders for the project (contents of the .nedfolders file)
        IContainer[] nedSourceFolders;

        // all projects we reference, directly or indirectly
        IProject[] referencedProjects;

        // non-duplicate toplevel (non-inner) types; keys are fully qualified names
        final Map<String, INedTypeInfo> components = new HashMap<String, INedTypeInfo>();

        // duplicate toplevel (non-inner) types; keys are fully qualified names
        final Map<String, List<INedTypeElement>> duplicates = new HashMap<String, List<INedTypeElement>>();

        // reserved (used) fully qualified names (contains all names including duplicates)
        final Set<String> reservedNames = new HashSet<String>();
    }

    // per-project tables. key-set is kept *strictly* up to date with the OMNeT++ projects,
    // so that projects.contains() should be used to determine whether a project is an OMNeT++ project
    private final Map<IProject,ProjectData> projects = new HashMap<IProject, ProjectData>();

    // if tables need to be rebuilt
    // DO NOT SET THIS DIRECTLY! Use invalidate().
    private boolean needsRehash = false;

    // For debugging: We increment this counter whenever a rehash occurs. Checks can be made
    // to assert that the function is not called unnecessarily
    private int debugRehashCounter = 0;

    // every NED change increments this counter. Checking against this counter allows one to
    // invalidate cached NED data whenever they potentially become stale.
    private long lastChangeSerial = 1;

    // file element to contain built-in declarations (does not correspond to any physical file)
    private NedFileElementEx builtInDeclarationsFile;

    private boolean nedModelChangeNotificationDisabled = false;
    private boolean refactoringInProgress = false;

    // cache for the method lookupNedType(String name, INedTypeLookupContext lookupContext)
    private Map<INedTypeLookupContext, Map<String, INedTypeInfo>> nedTypeLookupCache = new HashMap<INedTypeLookupContext, Map<String,INedTypeInfo>>();


    // delayed validation job
    private DelayedJob validationJob = new DelayedJob(400) {
        public void run() {
            DisplayUtils.runNowOrSyncInUIThread(new Runnable() {
                public void run() {
                    validateAllFiles();
                }
            });
        }
    };

    // listener, so that we don't need to make our nedModelChanged() method public
    private INedChangeListener nedModelChangeListener = new INedChangeListener() {
        public void modelChanged(NedModelEvent event) {
            nedModelChanged(event);
        }
    };

    /**
     * Constructor.
     */
    protected NedResources() {
        NedElement.setDefaultNedTypeResolver(this);
        createBuiltInNedTypes();
        // build the project table on init
        rebuildProjectsTable();
        // register as a workspace listener
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
    }

    public void dispose() {
        // remove ourselves from the listener list
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
    }

    public static NedResources getInstance() {
        if (instance == null)
            instance = new NedResources();
        return instance;
    }

    /**
     * Create channel and interface types that are predefined in NED.
     */
    protected void createBuiltInNedTypes() {
        String source = NEDParser.getBuiltInDeclarations();
        INedErrorStore errorStore = new SysoutNedErrorStore();
        builtInDeclarationsFile = NedTreeUtil.parseNedText(source, errorStore, "[builtin-declarations]");
        Assert.isTrue(errorStore.getNumProblems()==0);
    }

    // currently unused
    protected INedTypeInfo getBuiltInDeclaration(String name) {
        for (INedElement child : builtInDeclarationsFile)
            if (child instanceof INedTypeElement && ((INedTypeElement)child).getName().equals(name))
                return ((INedTypeElement)child).getNedTypeInfo();
        return null;
    }


    public boolean isRefactoringInProgress() {
        return refactoringInProgress;
    }

    public void setRefactoringInProgress(boolean refactoringInProgress) {
        this.refactoringInProgress = refactoringInProgress;
    }

    public long getLastChangeSerial() {
        return lastChangeSerial;
    }

    public INedTypeInfo createTypeInfoFor(INedTypeElement node) {
        return new NedTypeInfo(node);
    }

    public synchronized Set<IFile> getNedFiles() {
        return nedFiles.keySet();
    }

    public synchronized Set<IFile> getNedFiles(IProject project) {
        Set<IFile> files = new HashSet<IFile>();

        for (IFile file : nedFiles.keySet())
            if (project.equals(file.getProject()))
                files.add(file);

        return files;
    }

    public synchronized boolean containsNedFileElement(IFile file) {
        return nedFiles.containsKey(file);
    }

    public synchronized NedFileElementEx getNedFileElement(IFile file) {
        Assert.isTrue(nedFiles.containsKey(file), "file is not a NED file, or not parsed yet");
        return nedFiles.get(file);
    }

    public synchronized IFile getNedFile(NedFileElementEx nedFileElement) {
        Assert.isTrue(nedElementFiles.containsKey(nedFileElement) || nedFileElement==builtInDeclarationsFile, "NedFileElement is not in the resolver");
        return nedElementFiles.get(nedFileElement);
    }

    /**
     * NED text editors should call this when editor content changes.
     * Parses the given text, and synchronizes the stored NED model tree to it.
     * @param file - which file should be set
     * @param text - the textual content of the ned file
     */
    public synchronized void setNedFileText(IFile file, String text) {
        NedFileElementEx currentTree = getNedFileElement(file);

        // parse
        ProblemMarkerSynchronizer markerSync = new ProblemMarkerSynchronizer(NEDSYNTAXPROBLEM_MARKERID);
        markerSync.register(file);
        NedMarkerErrorStore errorStore = new NedMarkerErrorStore(file, markerSync);
        INedElement targetTree = NedTreeUtil.parseNedText(text, errorStore, file.getFullPath().toString());

        if (targetTree.getSyntaxProblemMaxCumulatedSeverity() == INedElement.SEVERITY_NONE) {
            NedTreeDifferenceUtils.Applier treeDifferenceApplier = new NedTreeDifferenceUtils.Applier();
            NedTreeDifferenceUtils.applyTreeDifferences(currentTree, targetTree, treeDifferenceApplier);

            if (treeDifferenceApplier.hasDifferences()) {
                // push tree differences into the official tree
//              Debug.println("pushing text editor changes into INedResources tree:\n  " + treeDifferenceApplier);
                currentTree.fireModelEvent(new NedBeginModelChangeEvent(currentTree));
                currentTree.setSyntaxProblemMaxLocalSeverity(INedElement.SEVERITY_NONE);
                treeDifferenceApplier.apply();
                currentTree.fireModelEvent(new NedEndModelChangeEvent(currentTree));

                // perform marker synchronization in a background job, to avoid deadlocks
                markerSync.runAsWorkspaceJob();

                // force rehash now, so that validation errors appear soon
                rehash();
            }
        }
        else {
            // mark the tree as having a syntax error, so that the graphical doesn't allow editing
            currentTree.setSyntaxProblemMaxLocalSeverity(IMarker.SEVERITY_ERROR);

            // perform marker synchronization in a background job, to avoid deadlocks
            markerSync.runAsWorkspaceJob();
        }
    }

    public IMarker[] getMarkersForElement(INedElement node, int limit) {
        try {
            IFile file = getNedFile(node.getContainingNedFileElement());
            List<IMarker> result = new ArrayList<IMarker>();
            for (IMarker marker : file.findMarkers(IMarker.PROBLEM, true, IFile.DEPTH_ZERO)) {
                int elementId = marker.getAttribute(NedMarkerErrorStore.NEDELEMENT_ID, -1);
                if (elementId != -1 && node.findElementWithId(elementId) != null)
                    result.add(marker);

                // skip the remaining after reaching limit
                if (result.size() >= limit)
                    break;
            }
            return result.toArray(new IMarker[]{});
        }
        catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized INedElement getNedElementAt(IFile file, int line, int column) {
        return getNedElementAt(getNedFileElement(file), line, column);
    }

    public synchronized INedElement getNedElementAt(INedElement parent, int line, int column) {
        for (INedElement child : parent)
            if (child.getSourceRegion() != null && child.getSourceRegion().contains(line, column))
                return getNedElementAt(child, line, column);
        return parent.getSourceRegion() != null && parent.getSourceRegion().contains(line, column) ? parent : null;
    }

    public synchronized Collection<INedTypeInfo> getNedTypesFromAllProjects() {
        // return everything from everywhere, including duplicates
        List<INedTypeInfo> result = new ArrayList<INedTypeInfo>();
        for (IFile file : nedFiles.keySet())
            for (INedElement child : nedFiles.get(file))
                if (child instanceof INedTypeElement)
                    result.add(((INedTypeElement)child).getNedTypeInfo());
        return result;
    }

    public synchronized Set<String> getNedTypeQNamesFromAllProjects() {
        // return everything from everywhere
        Set<String> result = new HashSet<String>();
        for (IFile file : nedFiles.keySet())
            for (INedElement child : nedFiles.get(file))
                if (child instanceof INedTypeElement)
                    result.add(((INedTypeElement)child).getNedTypeInfo().getFullyQualifiedName());
        return result;
    }

    public synchronized Set<INedTypeInfo> getNedTypesFromAllProjects(String qualifiedName) {
        Set<INedTypeInfo> result = new HashSet<INedTypeInfo>();
        for (IProject project : projects.keySet()) {
            INedTypeInfo nedType = getToplevelOrInnerNedType(qualifiedName, project);
            if (nedType != null)
                result.add(nedType);
        }
        return result;
    }

    public synchronized Collection<INedTypeInfo> getNedTypes(IProject context) {
        rehashIfNeeded();
        ProjectData projectData = projects.get(context);
        return projectData==null ? new ArrayList<INedTypeInfo>() : projectData.components.values();
    }

    public synchronized Collection<INedTypeInfo> getNedTypesThatImplement(INedTypeInfo interfaceType, IProject context) {
        Collection<INedTypeInfo> result = new ArrayList<INedTypeInfo>();
        Collection<INedTypeInfo> types = getNedTypes(context);
        for (INedTypeInfo type : types)
            if (type.getInterfaces().contains(interfaceType.getNedElement()))
                result.add(type);
        return result;
    }

    public synchronized Set<String> getNedTypeQNames(IPredicate predicate, IProject context) {
        Set<String> result = new HashSet<String>();
        for (INedTypeInfo typeInfo : getNedTypes(context))
            if (predicate.matches(typeInfo))
                result.add(typeInfo.getFullyQualifiedName());
        return result;
    }

    public synchronized Set<String> getNedTypeQNames(IProject context) {
        rehashIfNeeded();
        ProjectData projectData = projects.get(context);
        return projectData==null ? new HashSet<String>() : projectData.components.keySet();
    }

    public synchronized Set<String> getReservedQNames(IProject context) {
        rehashIfNeeded();
        ProjectData projectData = projects.get(context);
        return projectData==null ? new HashSet<String>() : projectData.reservedNames;
    }

    public synchronized Set<String> getReservedNames(IProject context, String packageName) {
        rehashIfNeeded();
        ProjectData projectData = projects.get(context);
        Set<String> result = new HashSet<String>();
        if (projectData != null) {
            String packagePrefix = StringUtils.isEmpty(packageName) ? "" : packageName + ".";
            for (String qualifiedName : projectData.reservedNames)
                if (qualifiedName.startsWith(packagePrefix))
                    result.add(StringUtils.removeStart(qualifiedName, packagePrefix));
        }
        return result;
    }

    public synchronized Set<String> getModuleQNames(IProject context) {
        return getNedTypeQNames(MODULE_FILTER, context);
    }

    public synchronized Set<String> getNetworkQNames(IProject context) {
        return getNedTypeQNames(NETWORK_FILTER, context);
    }

    public synchronized Set<String> getChannelQNames(IProject context) {
        return getNedTypeQNames(CHANNEL_FILTER, context);
    }

    public synchronized Set<String> getModuleInterfaceQNames(IProject context) {
        return getNedTypeQNames(MODULEINTERFACE_FILTER, context);
    }

    public synchronized Set<String> getChannelInterfaceQNames(IProject context) {
        return getNedTypeQNames(CHANNELINTERFACE_FILTER, context);
    }

    public synchronized INedTypeInfo getToplevelNedType(String qualifiedName, IProject context) {
        rehashIfNeeded();
        ProjectData projectData = projects.get(context);
        return projectData==null ? null : projectData.components.get(qualifiedName);
    }

    public synchronized INedTypeInfo getToplevelOrInnerNedType(String qualifiedName, IProject context) {
        rehashIfNeeded();
        ProjectData projectData = projects.get(context);
        if (projectData == null)
            return null;

        // try as toplevel type
        INedTypeInfo typeInfo = projectData.components.get(qualifiedName);

        // if not found, try as inner type
        if (typeInfo == null && qualifiedName.contains(".")) {
            INedTypeInfo enclosingType = projectData.components.get(StringUtils.substringBeforeLast(qualifiedName, "."));
            if (enclosingType != null) {
                INedTypeElement innerType = enclosingType.getInnerTypes().get(StringUtils.substringAfterLast(qualifiedName, "."));
                if (innerType != null)
                    typeInfo = innerType.getNedTypeInfo();
            }
        }
        return typeInfo;
    }

    public synchronized String getSimplePropertyFor(NedFileElementEx nedFileElement, String propertyName) {
        PropertyElementEx property = getPropertyFor(nedFileElement, propertyName);
        return property != null ? property.getSimpleValue() : null;
    }

    public synchronized String getSimplePropertyFor(IContainer folder, String propertyName) {
        PropertyElementEx property = getPropertyFor(folder, propertyName);
        return property != null ? property.getSimpleValue() : null;
    }

    public synchronized PropertyElementEx getPropertyFor(NedFileElementEx nedFileElement, String propertyName) {
        // look into this file, then into package.ned files in this folder and up
        PropertyElementEx property = NedElementUtilEx.getProperty(nedFileElement, propertyName, null);
        if (property != null)
            return property;
        return getPropertyFor(getNedFile(nedFileElement).getParent(), propertyName);
    }

    public synchronized PropertyElementEx getPropertyFor(IContainer folder, String propertyName) {
        // look for package.ned in this folder and up
        IContainer sourceFolder = getNedSourceFolderFor(folder);
        while (true) {
            IFile packageFile = folder.getFile(new Path(PACKAGE_NED_FILENAME));
            if (packageFile.exists()) {
                NedFileElementEx nedFileElement = getNedFileElement(packageFile);
                PropertyElementEx property = NedElementUtilEx.getProperty(nedFileElement, propertyName, null);
                if (property != null)
                    return property;
            }
            if (folder.equals(sourceFolder) || folder instanceof IProject)
                break;
            folder = folder.getParent();
        }
        return null;
    }

    public synchronized INedTypeInfo lookupNedType(String name, INedTypeLookupContext lookupContext) {
        // return cached value if exists, otherwise call doLookupNedType()
        Map<String, INedTypeInfo> map = nedTypeLookupCache.get(lookupContext);
        if (map == null)
            nedTypeLookupCache.put(lookupContext, map = new HashMap<String, INedTypeInfo>());
        INedTypeInfo typeInfo = map.get(name);
        // note: we need to distinguish between "null" meaning "not yet looked up", and
        // "looked up but no such type" (represented as: no such key vs value is null)
        if (typeInfo == null && !map.containsKey(name))
            map.put(name, typeInfo = doLookupNedType(name, lookupContext));
        return typeInfo;
    }

    // Internal method of lookupNedType -- not to be called directly
    protected INedTypeInfo doLookupNedType(String name, INedTypeLookupContext lookupContext) {
        rehashIfNeeded();
        Assert.isTrue(lookupContext!=null, "lookupNedType() cannot be called with context==null");

        // if (debug) Debug.println("looking up: " + name + " in " + lookupContext.debugString());

        // note: this method is to be kept consistent with NEDResourceCache::resolveNedType() in the C++ code
        // note2: partially qualified names are not supported: name must be either simple name or fully qualified
        IProject project = getNedFile(lookupContext.getContainingNedFileElement()).getProject();
        ProjectData projectData = projects.get(project);
        if (projectData == null)  // do not return type if the project is closed
            return null;
        if (name.contains(".")) {
            // contains dot, so it is a fully qualified name
            if (lookupContext instanceof CompoundModuleElementEx) {
                INedTypeInfo contextTypeInfo = ((CompoundModuleElementEx)lookupContext).getNedTypeInfo();

                // inner type with fully qualified name
                String prefix = StringUtils.substringBeforeLast(name, ".");
                String simpleName = StringUtils.substringAfterLast(name, ".");
                if (contextTypeInfo.getFullyQualifiedName().equals(prefix)) {
                    INedTypeElement innerType = contextTypeInfo.getInnerTypes().get(simpleName);
                    if (innerType != null)
                        return innerType.getNedTypeInfo();
                }
            }

            // fully qualified name (as we don't accept partially qualified names)
            if (projectData.components.get(name) != null)
                return projectData.components.get(name);
        }
        else {
            // no dot: name is an unqualified name (simple name); so, it can be:
            // (a) inner type, (b) an exactly imported type, (c) from the same package, (d) a wildcard imported type

            // inner type?
            if (lookupContext instanceof CompoundModuleElementEx) {
                // always lookup in the topmost compound module's context because "types:" is not allowed elsewhere
                CompoundModuleElementEx topLevelCompoundModule = (CompoundModuleElementEx)lookupContext.getParent().getParentWithTag(NedElementTags.NED_COMPOUND_MODULE);
                if (topLevelCompoundModule != null)
                    lookupContext = topLevelCompoundModule;
                INedTypeInfo contextTypeInfo = ((CompoundModuleElementEx)lookupContext).getNedTypeInfo();
                INedTypeElement innerType = contextTypeInfo.getInnerTypes().get(name);
                if (innerType != null)
                    return innerType.getNedTypeInfo();
            }

            // exactly imported type?
            // try a shortcut first: if the import doesn't contain wildcards
            List<String> imports = lookupContext.getContainingNedFileElement().getImports();
            for (String importSpec : imports)
                if (projectData.components.containsKey(importSpec) && (importSpec.endsWith("." + name) || importSpec.equals(name)))
                    return projectData.components.get(importSpec);

            // from the same package?
            String packagePrefix = lookupContext.getContainingNedFileElement().getQNameAsPrefix();
            INedTypeInfo samePackageType = projectData.components.get(packagePrefix + name);
            if (samePackageType != null)
                return samePackageType;

            // try harder, using wildcards
            String nameWithDot = "." + name;
            for (String importSpec : imports) {
                String importRegex = NedElementUtilEx.importToRegex(importSpec);
                for (String qualifiedName : projectData.components.keySet())
                    if ((qualifiedName.endsWith(nameWithDot) || qualifiedName.equals(name)) && qualifiedName.matches(importRegex))
                        return projectData.components.get(qualifiedName);
            }
        }
        return null;
    }

    public INedTypeInfo lookupLikeType(String name, INedTypeInfo interfaceType, IProject context) {
        if (name.contains(".")) {
            // must be a fully qualified name (as we don't accept partially qualified names)
            return getToplevelNedType(name, context);
        }
        else {
            // there must be exactly one NED type with that name that implements the given interface
            INedTypeInfo result = null;
            for (INedTypeInfo type : getNedTypes(context))  //XXX linear search
                if (type.getName().equals(name))
                    if (type.getInterfaces().contains(interfaceType.getNedElement()))
                        if (result != null)
                            return null; // more than one match --> error
                        else
                            result = type;
            return result;
        }
    }

    public synchronized Set<String> getVisibleTypeNames(INedTypeLookupContext lookupContext) {
        return getVisibleTypeNames(lookupContext, new IPredicate() {
            public boolean matches(INedTypeInfo typeInfo) {return true;}
        });
    }

    public synchronized Set<String> getVisibleTypeNames(INedTypeLookupContext lookupContext, IPredicate predicate) {
        rehashIfNeeded();

        // types from the same package
        String prefix = lookupContext.getQNameAsPrefix();
        String regex = prefix.replace(".", "\\.") + "[^.]+";
        Set<String> result = new HashSet<String>();

        IProject project = getNedFile(lookupContext.getContainingNedFileElement()).getProject();
        ProjectData projectData = projects.get(project);
        for (INedTypeInfo typeInfo : projectData.components.values())
            if (typeInfo.getFullyQualifiedName().matches(regex) && predicate.matches(typeInfo))
                result.add(typeInfo.getName());

        // imported types
        List<String> imports = lookupContext.getContainingNedFileElement().getImports();
        for (String importSpec : imports) {
            String importRegex = NedElementUtilEx.importToRegex(importSpec);
            for (INedTypeInfo typeInfo : projectData.components.values())
                if (typeInfo.getFullyQualifiedName().matches(importRegex) && predicate.matches(typeInfo))
                    result.add(typeInfo.getName());
        }
        return result;
    }


    /**
     * Determines if a resource is a NED file, that is, if it should be parsed.
     * It checks the file extension (".ned"), and whether the file is in one of
     * the NED source folders designated for the project.
     */
    public boolean isNedFile(IResource resource) {
        return (resource instanceof IFile &&
                NED_EXTENSION.equalsIgnoreCase(((IFile)resource).getFileExtension()) &&
                getNedSourceFolderFor((IFile)resource) != null);
    }

    public IContainer[] getNedSourceFolders(IProject project) {
        ProjectData projectData = projects.get(project);
        return projectData == null ? new IContainer[0] : projectData.nedSourceFolders;
    }

    public IContainer getNedSourceFolderFor(IFile file) {
        return getNedSourceFolderFor(file.getParent());
    }

    public IContainer getNedSourceFolderFor(IContainer folder) {
        IProject project = folder.getProject();
        ProjectData projectData = projects.get(project);
        if (projectData == null)
            return null;

        IContainer[] nedSourceFolders = projectData.nedSourceFolders;
        if (nedSourceFolders.length == 1 && nedSourceFolders[0] == project) // shortcut
            return project;

        for (IContainer container = folder; !container.equals(project); container = container.getParent())
            if (ArrayUtils.contains(nedSourceFolders, container))
                return container;
        return null;
    }

    public String getExpectedPackageFor(IFile file) {
        IContainer sourceFolder = getNedSourceFolderFor(file);
        if (sourceFolder == null)
            return null; // bad NED file
        if (sourceFolder.equals(file.getParent()) && file.getName().equals(PACKAGE_NED_FILENAME))
            return null; // nothing is expected: this file defines the package

        // first half is the package declared in the root "package.ned" file
        String packagePrefix = "";
        IFile packageNedFile = sourceFolder.getFile(new Path(PACKAGE_NED_FILENAME));
        if (getNedFiles().contains(packageNedFile))
            packagePrefix = StringUtils.nullToEmpty(getNedFileElement(packageNedFile).getPackage());

        // second half consists of the directories this file is down from the source folder
        String fileFolderPath = StringUtils.join(file.getParent().getFullPath().segments(), ".");
        String sourceFolderPath = StringUtils.join(sourceFolder.getFullPath().segments(), ".");
        Assert.isTrue(fileFolderPath.startsWith(sourceFolderPath));
        String packageSuffix = fileFolderPath.substring(sourceFolderPath.length());
        if (packageSuffix.length() > 0 && packageSuffix.charAt(0) == '.')
            packageSuffix = packageSuffix.substring(1);

        // concatenate
        String packageName = packagePrefix.length()>0 && packageSuffix.length()>0 ?
                packagePrefix + "." + packageSuffix :
                    packagePrefix + packageSuffix;
        return packageName;
    }

    /**
     * NED editors should call this when they get opened.
     */
    public synchronized void connect(IFile file) {
        if (connectCount.containsKey(file))
            connectCount.put(file, connectCount.get(file) + 1);
        else {
            if (!nedFiles.containsKey(file))
                readNedFile(file);
            connectCount.put(file, 1);
        }
    }

    /**
     * NED editors should call this when they get closed.
     */
    public synchronized void disconnect(IFile file) {
        Assert.isTrue(connectCount.containsKey(file));
        int count = connectCount.get(file); // must exist
        if (count <= 1) {
            // there's no open editor -- remove counter and re-read last saved
            // state from disk (provided it has not been deleted)
            connectCount.remove(file);
            if (file.exists())
                readNedFile(file);
        }
        else {
            connectCount.put(file, count - 1);
        }
    }

    public int getConnectCount(IFile file) {
        return connectCount.containsKey(file) ? connectCount.get(file) : 0;
    }

    public boolean hasConnectedEditor(IFile file) {
        return connectCount.containsKey(file);
    }

    /**
     * May only be called if the file is not already open in an editor.
     */
    public synchronized void readNedFile(IFile file) {
        ProblemMarkerSynchronizer markerSync = new ProblemMarkerSynchronizer();
        readNedFile(file, markerSync);
        markerSync.runAsWorkspaceJob();
        rehash();
    }

    private synchronized void readNedFile(IFile file, ProblemMarkerSynchronizer markerSync) {
        Assert.isTrue(!hasConnectedEditor(file));
        //Note: the following is a bad idea, because of undefined startup order: the editor calling us might run sooner than readAllNedFiles()
        //Assert.isTrue(isNEDFile(file), "file is outside the NED source folders, or not a NED file at all");

        if (debug)
            Debug.println("reading from disk: " + file.toString());

        // parse the NED file and put it into the hash table
        NedMarkerErrorStore errorStore = new NedMarkerErrorStore(file, markerSync, NEDSYNTAXPROBLEM_MARKERID);
        NedFileElementEx tree = NedTreeUtil.parseNedFile(file.getLocation().toOSString(), errorStore, file.getFullPath().toString());
        Assert.isNotNull(tree);

        storeNedFileModel(file, tree);
        invalidate();
    }

    /**
     * Forget a NED file, and throws out all cached info.
     */
    public synchronized void forgetNedFile(IFile file) {
        if (nedFiles.containsKey(file)) {
            // remove our model change listener from the file
            NedFileElementEx nedFileElement = nedFiles.get(file);
            nedFileElement.removeNedChangeListener(nedModelChangeListener);

            // unregister
            nedFiles.remove(file);
            nedElementFiles.remove(nedFileElement);
            invalidate();

            // fire notification.
            nedModelChanged(new NedFileRemovedEvent(file));
        }
    }

    // store NED file contents
    private synchronized void storeNedFileModel(IFile file, NedFileElementEx tree) {
        Assert.isTrue(!connectCount.containsKey(file), "cannot replace the tree while an editor is open");

        NedFileElementEx oldTree = nedFiles.get(file);
        // if the new tree has changed, we have to rehash everything
        if (oldTree == null || !NedTreeUtil.isNedTreeEqual(oldTree, tree)) {
            invalidate();
            nedFiles.put(file, tree);
            nedElementFiles.put(tree, file);
            // add ourselves to the tree root as a listener
            tree.addNedChangeListener(nedModelChangeListener);
            // remove ourselves from the old tree which is no longer used
            if (oldTree != null)
                oldTree.removeNedChangeListener(nedModelChangeListener);
            // fire a ned change notification (new tree added)
            nedModelChanged(new NedStructuralChangeEvent(tree, tree, NedStructuralChangeEvent.Type.INSERTION, tree, tree));
        }
    }

    private void rehash() {
        invalidate();
        rehashIfNeeded();
    }

    /**
     * Rebuild hash tables after NED resource change. Note: some errors such as
     * duplicate names only get detected when this gets run!
     */
    public synchronized void rehashIfNeeded() {
        if (!needsRehash)
            return;

        long startMillis = System.currentTimeMillis();

        needsRehash = false;
        debugRehashCounter++;

        // clear tables and re-register built-in declarations for all projects
        for (ProjectData projectData : projects.values()) {
            projectData.components.clear();
            projectData.duplicates.clear();
            projectData.reservedNames.clear();
            for (INedElement child : builtInDeclarationsFile) {
                if (child instanceof INedTypeElement) {
                    INedTypeInfo typeInfo = ((INedTypeElement)child).getNedTypeInfo();
                    projectData.components.put(typeInfo.getFullyQualifiedName(), typeInfo);
                }
            }
            projectData.reservedNames.addAll(projectData.components.keySet());
        }

        // register NED types in all projects
        for (IProject project : projects.keySet()) {
            ProjectData projectData = projects.get(project);

            // find NED types in each file, and register them
            for (IFile file : nedFiles.keySet()) {
                // file must be in this project or a referenced project
                if (file.getProject().equals(project) || ArrayUtils.contains(projectData.referencedProjects, file.getProject())) {

                    // collect toplevel types from the NED file, and process them one by one
                    for (INedElement child : nedFiles.get(file)) {
                        if (child instanceof INedTypeElement) {
                            INedTypeElement typeElement = (INedTypeElement) child;
                            INedTypeInfo typeInfo = typeElement.getNedTypeInfo();
                            String qualifiedName = typeInfo.getFullyQualifiedName();

                            if (projectData.reservedNames.contains(qualifiedName)) {
                                if (!projectData.duplicates.containsKey(qualifiedName))
                                    projectData.duplicates.put(qualifiedName, new ArrayList<INedTypeElement>());
                                projectData.duplicates.get(qualifiedName).add(typeElement);
                            }
                            else {
                                // normal case: not duplicate. Add the type info to our tables.
                                projectData.components.put(qualifiedName, typeInfo);
                            }

                            // add to the name list even if it was duplicate
                            projectData.reservedNames.add(qualifiedName);
                        }
                    }
                }
            }

            // now we should remove all types that were duplicates
            for (String name : projectData.duplicates.keySet()) {
                projectData.duplicates.get(name).add(projectData.components.get(name).getNedElement());
                projectData.components.remove(name);
            }

            if (debug)
                Debug.println("types in project " + project.getName() + ": " + StringUtils.join(projectData.components.keySet(), ", ", " and "));

        }

        if (debug) {
            long dt = System.currentTimeMillis() - startMillis;
            Debug.println("rehash(): " + dt + "ms, " + nedFiles.size() + " files, " + projects.size() + " projects");
        }

        // schedule a validation
        validationJob.restartTimer();
    }

    protected void invalidateTypeInfo(INedElement parent) {
        for (INedElement element : parent) {
            if (element instanceof INedTypeElement) {
                // invalidate
                ((INedTypeElement)element).getNedTypeInfo().invalidateInherited();

                // do inner types too
                if (element instanceof CompoundModuleElementEx) {
                    INedElement typesSection = ((CompoundModuleElementEx)element).getFirstTypesChild();
                    if (typesSection != null)
                        invalidateTypeInfo(typesSection);
                }
            }
        }
    }

    /**
     * Validates all NED files for consistency (no such parameter/gate/module-type, redeclarations,
     * duplicate types, cycles in the inheritance chain, etc). All consistency problem markers
     * (NEDCONSISTENCYPROBLEM_MARKERID) are managed within this method.
     */
    public synchronized void validateAllFiles() {
        long startMillis = System.currentTimeMillis();

        // During validation, we potentially fire a large number of NEDMarkerChangeEvents.
        // So we'll surround the code with begin..end notifications, which allows the
        // graphical editor to optimize refresh() calls. Otherwise it would have to
        // refresh on each notification, which can be a disaster performance-wise.

        // fake a begin change event, then "finally" an end change event
        fireBeginChangeEvent();
        if (debug)
            Debug.println("Validation started");
        ProblemMarkerSynchronizer markerSync = new ProblemMarkerSynchronizer(NEDCONSISTENCYPROBLEM_MARKERID);
        try {
            // clear consistency error markers from the ned tree
            for (IFile file : nedFiles.keySet())
                nedFiles.get(file).clearConsistencyProblemMarkerSeverities();

            // issue error message for duplicates
            for (IProject project : projects.keySet()) {
                ProjectData projectData = projects.get(project);
                for (String name : projectData.duplicates.keySet()) {
                    List<INedTypeElement> duplicateList = projectData.duplicates.get(name);
                    for (int i = 0; i < duplicateList.size(); i++) {
                        INedTypeElement element = duplicateList.get(i);
                        INedTypeElement otherElement = duplicateList.get(i==0 ? 1 : 0);
                        IFile file = getNedFile(element.getContainingNedFileElement());
                        IFile otherFile = getNedFile(otherElement.getContainingNedFileElement());

                        NedMarkerErrorStore errorStore = new NedMarkerErrorStore(file, markerSync);
                        if (otherFile == null) {
                            errorStore.addError(element, element.getReadableTagName() + " '" + name + "' is a built-in type and cannot be redefined");
                        }
                        else {
                            // add error message to both files
                            String messageHalf = element.getReadableTagName() + " '" + name + "' already defined in ";
                            errorStore.addError(element, messageHalf + otherFile.getFullPath().toString());
                            NedMarkerErrorStore otherErrorStore = new NedMarkerErrorStore(otherFile, markerSync);
                            otherErrorStore.addError(otherElement, messageHalf + file.getFullPath().toString());
                        }
                    }
                }
            }

            // validate all files
            for (IFile file : nedFiles.keySet()) {
                NedFileElementEx nedFileElement = nedFiles.get(file);
                markerSync.register(file);
                INedErrorStore errorStore = new NedMarkerErrorStore(file, markerSync);
                //INedErrorStore errorStore = new INedErrorStore.SysoutNedErrorStore(); // for debugging
                new NedValidator(this, file.getProject(), errorStore).validate(nedFileElement);
            }

            // we need to do the synchronization in a background job, to avoid deadlocks
            markerSync.runAsWorkspaceJob();

        }
        finally {
            fireEndChangeEvent();
        }

        if (debug) {
            long dt = System.currentTimeMillis() - startMillis;
            Debug.println("validateAllFiles(): " + dt + "ms, " + markerSync.getNumberOfMarkers() + " markers on " + markerSync.getNumberOfFiles() + " files");
            Debug.println("typeinfo: refreshLocalCount:" + NedTypeInfo.debugRefreshLocalCount + "  refreshInheritedCount:" + NedTypeInfo.debugRefreshInheritedCount);
        }
    }

    public synchronized void fireBeginChangeEvent() {
        nedModelChanged(new NedBeginModelChangeEvent(null));
    }

    public synchronized void fireEndChangeEvent() {
        nedModelChanged(new NedEndModelChangeEvent(null));
    }

    //XXX method not currently used
    public synchronized void runWithBeginEndNotification(Runnable runnable) {
        fireBeginChangeEvent();
        try {
            runnable.run();
        } finally {
            fireEndChangeEvent();
        }
    }

    public synchronized void invalidate() {
        lastChangeSerial++;
        needsRehash = true;
        nedTypeLookupCache.clear();

        // invalidate all inherited members on all typeInfo objects
        for (NedFileElementEx file : nedElementFiles.keySet())
            invalidateTypeInfo(file);
    }

    /**
     * To be called on project-level changes: project open/close, project description change
     * (i.e. nature & referred projects), ".nedfolders" file.
     *
     * Also needs to be invoked right on startup, to prevent race conditions with editors.
     * (When an editor starts, the projects table must already be up to date, otherwise
     * the editor's input file might not qualify as "NED file" and that'll cause an error).
     */
    //FIXME call from ctor?
    public synchronized void rebuildProjectsTable() {
        // rebuild table
        projects.clear();
        IProject[] omnetppProjects = ProjectUtils.getOmnetppProjects();
        for (IProject project : omnetppProjects) {
            try {
                ProjectData projectData = new ProjectData();
                projectData.referencedProjects = ProjectUtils.getAllReferencedOmnetppProjects(project);
                projectData.nedSourceFolders = ProjectUtils.readNedFoldersFile(project);
                projects.put(project, projectData);
            }
            catch (Exception e) {
                NedResourcesPlugin.logError(e); //XXX anything else? asyncExec errorDialog?
            }
        }
        dumpProjectsTable();

        // forget those files which are no longer in our projects or NED folders
        // Note: use "trash" list to avoid ConcurrentModificationException in nedFiles
        List <IFile> trash = new ArrayList<IFile>();
        for (IFile file : nedFiles.keySet())
            if (!isNedFile(file))
                trash.add(file);
        try {
            fireBeginChangeEvent();
            for (IFile file : trash)
                forgetNedFile(file);
        } finally {
            fireEndChangeEvent();
        }

        // invalidate because project dependencies might have changed, even if there was no NED change
        invalidate();
        nedModelChanged(new NedModelChangeEvent(null));  // "anything might have changed"
        scheduleReadMissingNedFiles();
    }

    /**
     * Schedules a background job to read NED files that are not yet loaded.
     */
    public void scheduleReadMissingNedFiles() {
        WorkspaceJob job = new WorkspaceJob("Parsing NED files...") {
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                readMissingNedFiles();
                return Status.OK_STATUS;
            }
        };
        job.setRule(ResourcesPlugin.getWorkspace().getRoot());
        job.setPriority(Job.SHORT);
        job.setSystem(false);
        job.schedule();
    }

    /**
     * Reads NED files that are not yet loaded (not in our nedFiles table).
     * This should be run on startup and after rebuildProjectsTable();
     * individual file changes are handled by loadNedFile() calls from the
     * workspace listener.
     */
    public synchronized void readMissingNedFiles() {
        try {
            // disable all ned model notifications until all files have been processed
            nedModelChangeNotificationDisabled = true;
            debugRehashCounter = 0;

            // read NED files that are not yet loaded
            final ProblemMarkerSynchronizer sync = new ProblemMarkerSynchronizer();
            IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
            workspaceRoot.accept(new IResourceVisitor() {
                public boolean visit(IResource resource) {
                    if (!nedFiles.containsKey(resource) && isNedFile(resource))
                        readNedFile((IFile)resource, sync);
                    return true;
                }
            });
            sync.runAsWorkspaceJob();
            rehashIfNeeded();
        }
        catch (CoreException e) {
            NedResourcesPlugin.logError("Error during workspace refresh: ",e);
        } finally {
            nedModelChangeNotificationDisabled = false;
            Assert.isTrue(debugRehashCounter <= 1, "Too many rehash operations during readAllNedFilesInWorkspace()");
            nedModelChanged(new NedModelChangeEvent(null));  // "everything changed"
        }
    }

    // ******************* notification helpers ************************************

    public void addNedModelChangeListener(INedChangeListener listener) {
        if (nedModelChangeListenerList == null)
            nedModelChangeListenerList = new NedChangeListenerList();
        nedModelChangeListenerList.add(listener);
    }

    public void removeNedModelChangeListener(INedChangeListener listener) {
        if (nedModelChangeListenerList != null)
            nedModelChangeListenerList.remove(listener);
    }

    /**
     * Respond to model changes
     */
    protected void nedModelChanged(NedModelEvent event) {
        //Debug.println("**** nedModelChanged - notify");
        if (nedModelChangeNotificationDisabled)
            return;

        if (event instanceof NedModelChangeEvent) {
            INedElement source = ((NedModelChangeEvent)event).getSource();
            Assert.isTrue(source==null || refactoringInProgress || source instanceof NedFileElementEx || hasConnectedEditor(getNedFile(source.getContainingNedFileElement())), "NED trees not opened in any editor must NOT be changed");
            invalidate();
            validationJob.restartTimer(); //FIXME obey begin/end notifications too!
        }

        // notify generic listeners (like NedFileEditParts who refresh themselves
        // in response to this notification)
        // long startMillis = System.currentTimeMillis();

        if (nedModelChangeListenerList != null)
            nedModelChangeListenerList.fireModelChanged(event);

        // long dt = System.currentTimeMillis() - startMillis;
        // Debug.println("visual notification took " + dt + "ms");
    }

    /**
     * Synchronize the plugin with the resources in the workspace
     */
    public synchronized void resourceChanged(IResourceChangeEvent event) {
        try {
            if (event.getDelta() == null)
                return;
            //printResourceChangeEvent(event);
            final ProblemMarkerSynchronizer sync = new ProblemMarkerSynchronizer();
            event.getDelta().accept(new IResourceDeltaVisitor() {
                public boolean visit(IResourceDelta delta) throws CoreException {
                    IResource resource = delta.getResource();
                    // printDelta(delta);
                    if (isNedFile(resource)) {
                        IFile file = (IFile)resource;
                        switch (delta.getKind()) {
                        case IResourceDelta.REMOVED:
                            forgetNedFile(file);
                            break;
                        case IResourceDelta.ADDED:
                            readNedFile(file, sync);
                            break;
                        case IResourceDelta.CHANGED:
                            // we are only interested in content changes; ignore marker and property changes
                            if ((delta.getFlags() & IResourceDelta.CONTENT) != 0 && !hasConnectedEditor(file))
                                readNedFile(file, sync);
                            break;
                        }
                    }
                    else if (ProjectUtils.isNedFoldersFile(resource)) {
                        rebuildProjectsTable();
                    }
                    else if (resource instanceof IProject) {
                        switch (delta.getKind()) {
                        case IResourceDelta.REMOVED:
                        case IResourceDelta.ADDED:
                        case IResourceDelta.OPEN:
                            rebuildProjectsTable();
                            break;
                        case IResourceDelta.CHANGED:
                            // change in natures and referenced projects will be reported as description changes
                            if ((delta.getFlags() & IResourceDelta.DESCRIPTION) != 0)
                                rebuildProjectsTable();
                            break;
                        }
                    }
                    return true;
                }
            });
            sync.runAsWorkspaceJob();
        }
        catch (CoreException e) {
            NedResourcesPlugin.logError("Error during workspace change notification: ", e);
        } finally {
            rehashIfNeeded();
        }

    }

    // Utility functions for debugging
    public static void printResourceChangeEvent(IResourceChangeEvent event) {
        Debug.println("event type: "+event.getType());
    }

    public static void printDelta(IResourceDelta delta) {
        // LEGEND: [+] added, [-] removed, [*] changed, [>] and [<] phantom added/removed;
        // then: {CONTENT, MOVED_FROM, MOVED_TO, OPEN, TYPE, SYNC, MARKERS, REPLACED, DESCRIPTION, ENCODING}
        Debug.println("  "+((ResourceDelta)delta).toDebugString());
    }

    public void dumpProjectsTable() {
        Debug.println(projects.size() + " projects:");
        for (IProject project : projects.keySet()) {
            ProjectData projectData = projects.get(project);
            Debug.println("  " + project.getName() + ":" +
                    "  deps: " + StringUtils.join(projectData.referencedProjects, ",") +
                    "  nedfolders: " + StringUtils.join(projectData.nedSourceFolders, ","));
        }
    }
}
