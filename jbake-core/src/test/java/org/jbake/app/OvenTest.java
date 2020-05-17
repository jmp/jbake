package org.jbake.app;

import org.jbake.TestUtils;
import org.jbake.app.configuration.ConfigUtil;
import org.jbake.app.configuration.DefaultJBakeConfiguration;
import org.jbake.model.DocumentTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class OvenTest {

    @TempDir
    File root;

    private DefaultJBakeConfiguration configuration;
    private File sourceFolder;
    private ContentStore contentStore;

    @BeforeEach
    public void setUp() throws Exception {
        // reset values to known state otherwise previous test case runs can affect the success of this test case
        DocumentTypes.resetDocumentTypes();
        File output = new File(root, "output");
        sourceFolder = TestUtils.getTestResourcesAsSourceFolder();
        configuration = (DefaultJBakeConfiguration) new ConfigUtil().loadConfig(sourceFolder);
        configuration.setDestinationFolder(output);
        configuration.setTemplateFolder(new File(sourceFolder, "freemarkerTemplates"));
    }

    @AfterEach
    public void tearDown() {
        if (contentStore != null && contentStore.isActive()) {
            contentStore.close();
            contentStore.shutdown();
        }
    }

    @Test
    public void bakeWithAbsolutePaths() {
        configuration.setTemplateFolder(new File(sourceFolder, "freemarkerTemplates"));
        configuration.setContentFolder(new File(sourceFolder, "content"));
        configuration.setAssetFolder(new File(sourceFolder, "assets"));

        final Oven oven = new Oven(configuration);
        oven.bake();

        assertThat(oven.getErrors()).isEmpty();
    }

    @Test
    public void shouldBakeWithRelativeCustomPaths() throws Exception {
        sourceFolder = TestUtils.getTestResourcesAsSourceFolder("/fixture-custom-relative");
        configuration = (DefaultJBakeConfiguration) new ConfigUtil().loadConfig(sourceFolder);
        File assetFolder = new File(configuration.getDestinationFolder(), "css");
        File aboutFile = new File(configuration.getDestinationFolder(), "about.html");
        File blogSubFolder = new File(configuration.getDestinationFolder(), "blog");


        final Oven oven = new Oven(configuration);
        oven.bake();

        assertThat(oven.getErrors()).isEmpty();
        assertThat(configuration.getDestinationFolder()).isNotEmptyDirectory();
        assertThat(assetFolder).isNotEmptyDirectory();
        assertThat(aboutFile).isFile();
        assertThat(aboutFile).isNotEmpty();
        assertThat(blogSubFolder).isNotEmptyDirectory();
    }

    @Test
    public void shouldThrowExceptionIfSourceFolderDoesNotExist() {
        configuration.setSourceFolder(new File(root, "none"));

        assertThrows(JBakeException.class, () -> new Oven(configuration));
    }

    @Test
    public void shouldInstantiateNeededUtensils() throws Exception {

        File template = TestUtils.newFolder(root, "template");
        File content = TestUtils.newFolder(root, "content");
        File assets = TestUtils.newFolder(root, "assets");

        configuration.setTemplateFolder(template);
        configuration.setContentFolder(content);
        configuration.setAssetFolder(assets);

        Oven oven = new Oven(configuration);

        assertThat(oven.getUtensils().getContentStore()).isNotNull();
        assertThat(oven.getUtensils().getCrawler()).isNotNull();
        assertThat(oven.getUtensils().getRenderer()).isNotNull();
        assertThat(oven.getUtensils().getAsset()).isNotNull();
        assertThat(oven.getUtensils().getConfiguration()).isEqualTo(configuration);
    }

    @Test()
    public void shouldInspectConfigurationDuringInstantiationFromUtils() {
        configuration.setSourceFolder(new File(root, "none"));

        Utensils utensils = new Utensils();
        utensils.setConfiguration(configuration);

        assertThrows(JBakeException.class, () -> new Oven(utensils));
    }

    @Test
    public void shouldCrawlRenderAndCopyAssets() throws Exception {
        File template = TestUtils.newFolder(root, "template");
        File content = TestUtils.newFolder(root, "content");
        File assets = TestUtils.newFolder(root, "assets");

        configuration.setTemplateFolder(template);
        configuration.setContentFolder(content);
        configuration.setAssetFolder(assets);

        contentStore = spy(new ContentStore("memory", "documents" + System.currentTimeMillis()));

        Crawler crawler = mock(Crawler.class);
        Renderer renderer = mock(Renderer.class);
        Asset asset = mock(Asset.class);

        Utensils utensils = new Utensils();
        utensils.setConfiguration(configuration);
        utensils.setContentStore(contentStore);
        utensils.setRenderer(renderer);
        utensils.setCrawler(crawler);
        utensils.setAsset(asset);

        Oven oven = new Oven(utensils);

        oven.bake();

        verify(contentStore, times(1)).startup();
        verify(renderer, atLeastOnce()).renderIndex(anyString());
        verify(crawler, times(1)).crawl();
        verify(asset, times(1)).copy();
    }
}
