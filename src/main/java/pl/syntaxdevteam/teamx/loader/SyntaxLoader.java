package pl.syntaxdevteam.teamx.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class SyntaxLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        try (InputStream stream = SyntaxLoader.class.getResourceAsStream("/paper-libraries.yml")) {
            if (stream == null) {
                throw new IllegalStateException("TeamX is missing paper-libraries.yml");
            }
            Map<String, Object> manifest = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
            Map<?, ?> repositories = (Map<?, ?>) manifest.get("repositories");
            List<?> libraries = (List<?>) manifest.get("libraries");
            MavenLibraryResolver resolver = new MavenLibraryResolver();
            repositories.forEach((id, value) -> {
                String url = "MAVEN_CENTRAL_DEFAULT_MIRROR".equals(value)
                    ? MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
                    : (String) value;
                resolver.addRepository(new RemoteRepository.Builder((String) id, "default", url).build());
            });
            for (Object library : libraries) {
                resolver.addDependency(new Dependency(new DefaultArtifact((String) library), null, false,
                    List.of(new Exclusion("net.kyori", "*", "*", "*"))));
            }
            classpath.addLibrary(resolver);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the TeamX runtime library manifest", exception);
        }
    }
}
