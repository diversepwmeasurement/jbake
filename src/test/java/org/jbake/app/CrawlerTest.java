package org.jbake.app;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jbake.app.ConfigUtil.Keys;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CrawlerTest {
    private CompositeConfiguration config;
    private ContentStore db;
    private File sourceFolder;

    @Before
    public void setup() throws Exception, IOException, URISyntaxException {
        URL sourceUrl = this.getClass().getResource("/");

        sourceFolder = new File(sourceUrl.getFile());
        if (!sourceFolder.exists()) {
            throw new Exception("Cannot find sample data structure!");
        }

        config = ConfigUtil.load(new File(this.getClass().getResource("/").getFile()));
        Assert.assertEquals(".html", config.getString(Keys.OUTPUT_EXTENSION));
        db = DBUtil.createDataStore("memory", "documents" + System.currentTimeMillis());
    }

    @After
    public void cleanup() throws InterruptedException {
        db.drop();
        db.close();
    }

    @Test
    public void crawl() throws ConfigurationException {
        Crawler crawler = new Crawler(db, sourceFolder, config);
        crawler.crawl(new File(sourceFolder.getPath() + File.separator + config.getString(Keys.CONTENT_FOLDER)));

        Assert.assertEquals(3, db.getDocumentCount("post"));
        Assert.assertEquals(3, db.getDocumentCount("page"));

        DocumentList results = db.getPublishedPosts();

        assertThat(results.size()).isEqualTo(2);

        for (Map<String, Object> content : results) {
            assertThat(content)
                    .containsKey(Crawler.Attributes.ROOTPATH)
                    .containsValue("../../");
        }

        DocumentList allPosts = db.getAllContent("post");

        assertThat(allPosts.size()).isEqualTo(3);

        for (Map<String, Object> content : allPosts) {
            if (content.get(Crawler.Attributes.TITLE).equals("Draft Post")) {
                assertThat(content).containsKey(Crawler.Attributes.DATE);
            }
        }

        // covers bug #213
        DocumentList publishedPostsByTag = db.getPublishedPostsByTag("blog");
        Assert.assertEquals(2, publishedPostsByTag.size());
    }

    @Test
    public void renderWithPrettyUrls() throws Exception {
        Map<String, Object> testProperties = new HashMap<String, Object>();
        testProperties.put(Keys.URI_NO_EXTENSION, true);
        testProperties.put(Keys.URI_NO_EXTENSION_PREFIX, "/blog");

        CompositeConfiguration config = new CompositeConfiguration();
        config.addConfiguration(new MapConfiguration(testProperties));
        config.addConfiguration(ConfigUtil.load(new File(this.getClass().getResource("/").getFile())));

        Crawler crawler = new Crawler(db, sourceFolder, config);
        crawler.crawl(new File(sourceFolder.getPath() + File.separator + config.getString(Keys.CONTENT_FOLDER)));

        Assert.assertEquals(3, db.getDocumentCount("post"));
        Assert.assertEquals(3, db.getDocumentCount("page"));

        DocumentList documents = db.getPublishedPosts();

        for (Map<String, Object> model : documents) {
            String noExtensionUri = "blog/\\d{4}/" + FilenameUtils.getBaseName((String) model.get("file")) + "/";

            Assert.assertThat(model.get("noExtensionUri"), RegexMatcher.matches(noExtensionUri));
            Assert.assertThat(model.get("uri"), RegexMatcher.matches(noExtensionUri + "index\\.html"));
        }
    }

    private static class RegexMatcher extends BaseMatcher<Object> {
        private final String regex;

        public RegexMatcher(String regex) {
            this.regex = regex;
        }

        @Override
        public boolean matches(Object o) {
            return ((String) o).matches(regex);

        }

        @Override
        public void describeTo(Description description) {
            description.appendText("matches regex: " + regex);
        }

        public static RegexMatcher matches(String regex) {
            return new RegexMatcher(regex);
        }
    }
}
