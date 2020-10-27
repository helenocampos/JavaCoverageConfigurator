/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package info.heleno;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Build;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Developer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Resource;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 *
 * @author helenocampos
 */
public class App {

    static String jacocoVersion = "0.8.2";

    public static void main(String[] args) {
        if (args.length > 0) {
            String projectPath = args[0];
            Model parentPom = readPom(projectPath);
            if (isMultiModuleProject(parentPom)) {
//                System.out.println("Multi module project. Creating aggregator module.");
                aggregatePom(projectPath, parentPom);
            } else {
                addJacocoPlugin(parentPom);
                writePom(parentPom, projectPath);
            }
            checkSurefireArgLine(parentPom, projectPath);
        }
    }

    private static void aggregatePom(String projectPath, Model parentPom) {

        if (isMultiModuleProject(parentPom)) {
            List<Model> modules = getAllSubModulesPoms(parentPom, projectPath);
            String groupIdModule = getModulesGroupId(parentPom, modules);
            File aggregatorFolder = new File(projectPath, "aggregator");
            aggregatorFolder.mkdir();
            Model aggregatorModel = getAggregatorModelPom(parentPom.getArtifactId(),
                    parentPom.getVersion(), parentPom.getGroupId(), modules, groupIdModule);

            writePom(aggregatorModel, aggregatorFolder.getAbsolutePath());

            addJacocoPlugin(parentPom);
            if (!parentPom.getModules().contains("aggregator")) {
                parentPom.addModule("aggregator");
            }

            writePom(parentPom, projectPath);
        }

    }

    private static void setArgLine(Map<String, Plugin> plugins) {
        if (plugins != null) {
            Plugin surefire = plugins.get("org.apache.maven.plugins:maven-surefire-plugin");
            if (surefire != null) {
                Xpp3Dom config = (Xpp3Dom) surefire.getConfiguration();
                if (config != null) {
                    Xpp3Dom argLineNode = config.getChild("argLine");
                    if (argLineNode != null) {
                        String argLine = argLineNode.getValue();
                        if (!argLine.contains("@{argLine}") && !argLine.contains("${argLine}")) {
                            argLine = argLine + " ${argLine}";
                            argLineNode.setValue(argLine);
                        }
                    }

                }

            }
        }
    }

    private static void checkSurefireArgLine(Model pom, String projectPath) {
        Build build = pom.getBuild();
        if (build != null) {
            setArgLine(build.getPluginsAsMap());
            PluginManagement pluginManagement = build.getPluginManagement();
            if(pluginManagement!=null){
                setArgLine(pluginManagement.getPluginsAsMap());
            }
            writePom(pom, projectPath);
        }
    }

    private static List<Model> getAllSubModulesPoms(Model pom, String projectPath) {
        List<Model> modules = new LinkedList<>();
        List<String> modulesFoldersName = pom.getModules();
        if (modulesFoldersName != null && !modulesFoldersName.isEmpty()) {
            for (String moduleFolderName : modulesFoldersName) {
                File moduleFolder = new File(projectPath, moduleFolderName);
                if (moduleFolder.exists()) {
                    Model modulePom = readPom(moduleFolder.getAbsolutePath());
                    if (modulePom != null && !includesAnotherPom(modulePom)) {
                        if (!modulePom.getPackaging().equals("pom")) {
                            modules.add(modulePom);
                        }
                        if (isMultiModuleProject(modulePom)) {
                            modules.addAll(getAllSubModulesPoms(modulePom, moduleFolder.getAbsolutePath()));
                        }
                    }
                }
            }
        }
        return modules;
    }

    private static boolean includesAnotherPom(Model pom) {
        boolean includesAnotherPom = false;
        Build build = pom.getBuild();
        if (build != null) {
            List<Resource> resources = build.getResources();
            if (resources != null) {
                for (Resource resource : resources) {
                    List<String> includes = resource.getIncludes();
                    if (includes != null) {
                        for (String include : includes) {
                            if (include.contains("pom.xml")) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return includesAnotherPom;
    }

    private static String getModulesGroupId(Model pom, List<Model> submodules) {
        String groupIdModule = pom.getGroupId();
        if (groupIdModule == null || groupIdModule.isEmpty()) {
            for (Model submodule : submodules) {
                if (groupIdModule == null || groupIdModule.isEmpty()) {
                    return submodule.getGroupId();
                }
            }
        }

        return groupIdModule;
    }

    private static boolean isMultiModuleProject(Model pom) {
        return !pom.getModules().isEmpty();
    }

    private static Model addJacocoPlugin(Model pom) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.jacoco");
        plugin.setArtifactId("jacoco-maven-plugin");
        plugin.setVersion("0.8.2");
        PluginExecution pluginExecution = new PluginExecution();
        pluginExecution.addGoal("prepare-agent");
        pluginExecution.setId("prepare-agent");
        plugin.addExecution(pluginExecution);

        PluginExecution report = new PluginExecution();
        report.addGoal("report");
        report.setId("coverage-report");
        plugin.addExecution(report);

        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
        }
        build.setPlugins(removePlugin("jacoco-maven-plugin", build.getPlugins()));
        build.addPlugin(plugin);
        pom.setBuild(build);

        return pom;
    }

    private static List<Plugin> removePlugin(String artifactId, List<Plugin> plugins) {
        Iterator<Plugin> pluginsIterator = plugins.iterator();
        while (pluginsIterator.hasNext()) {
            Plugin plugin = pluginsIterator.next();
            if (plugin.getArtifactId().equals(artifactId)) {
                pluginsIterator.remove();
            }
        }
        return plugins;
    }

    public static Model getAggregatorModelPom(String parentId, String parentVersion, String parentGroupId, List<Model> dependencies, String moduleGroupId) {
        Model pomModel = new Model();
        pomModel.setModelVersion("4.0.0");
        Parent parent = new Parent();
        parent.setArtifactId(parentId);
        parent.setGroupId(parentGroupId);
        parent.setVersion(parentVersion);
        pomModel.setParent(parent);
        for (Model dependency : dependencies) {
            Dependency dep = new Dependency();
            if(dependency.getPackaging()!=null){
                dep.setType(dependency.getPackaging());
            }
            if (dependency.getGroupId() != null) {
                dep.setGroupId(dependency.getGroupId());
            } else {
                dep.setGroupId(moduleGroupId);
            }
            if (dependency.getArtifactId() != null) {
                dep.setArtifactId(dependency.getArtifactId());
            }
            if (dependency.getVersion() != null) {
                dep.setVersion(dependency.getVersion());
            } else {
                dep.setVersion("${project.version}");
            }

            pomModel.addDependency(dep);
        }
        pomModel.setArtifactId("aggregator");
        pomModel.setGroupId(moduleGroupId);
        pomModel.setVersion(parentVersion);

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.jacoco");
        plugin.setArtifactId("jacoco-maven-plugin");
        plugin.setVersion("0.8.2");
        PluginExecution pluginExecution = new PluginExecution();
        pluginExecution.setId("report-aggregate");
        pluginExecution.setPhase("test");
        pluginExecution.addGoal("report-aggregate");
        plugin.addExecution(pluginExecution);

        Build build = new Build();
        build.addPlugin(plugin);

        pomModel.setBuild(build);

        return pomModel;
    }

    public static Model readPom(String projectFolder) {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        File newProjectDir = new File(projectFolder);
        Model pomModel = null;
        if (newProjectDir.exists()) {
            File pom = new File(newProjectDir, "pom.xml");
            if (pom.exists()) {
                try {
                    pomModel = reader.read(new FileReader(pom));
                } catch (Exception ex) {
//                    Logger.getLogger(MyMojo.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return pomModel;
    }

    public static void writePom(Model model, String projectFolder) {
        File newProjectDir = new File(projectFolder);
        if (newProjectDir.exists()) {
            File pom = new File(newProjectDir, "pom.xml");

            try {
                DefaultModelWriter writer = new DefaultModelWriter();
                OutputStream output = new FileOutputStream(pom);
                replaceSpecialCharacters(model);
                writer.write(output, null, model);
                output.close();
            } catch (FileNotFoundException ex) {
//                    Logger.getLogger(MyMojo.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
//                    Logger.getLogger(MyMojo.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    private static void replaceSpecialCharacters(Model model) {
        List<Contributor> contributors = model.getContributors();
        if (contributors != null) {
            for (Contributor contributor : contributors) {
                String name = contributor.getName();
                if (name != null) {
                    contributor.setName(Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}", ""));
                }
            }
        }
        List<Developer> developers = model.getDevelopers();
        if (developers != null) {
            for (Developer developer : developers) {
                String name = developer.getName();
                if (name != null) {
                    developer.setName(Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}", ""));
                }
            }
        }
    }
}
