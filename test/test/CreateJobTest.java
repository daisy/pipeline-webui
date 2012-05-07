package test;

import org.junit.*;

import com.thoughtworks.selenium.Selenium;

import play.mvc.*;
import play.test.*;
import play.libs.F.*;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

public class CreateJobTest {

    @Test
    public void runInBrowser() {
        running(testServer(9000), HTMLUNIT, new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
            	
            	// Log in
            	browser.goTo("http://localhost:9000");
            	assertThat(browser.$("title").getTexts().get(0)).isEqualTo("Sign in");
            	browser.fill("#email").with("josteinaj@gmail.com");
            	browser.fill("#password").with("jostein");
            	browser.submit("#submit");
            	
            	// Go to zedai-to-epub3 script
            	assertThat(browser.$("title").getTexts().get(0)).isEqualTo("Scripts");
            	browser.goTo("http://localhost:9000/scripts/zedai-to-epub3");
            	assertThat(browser.$("title").getTexts().get(0)).isEqualTo("Create job");
            	
            	// Upload ZIP file
            	
            	// TODO: was planning to write a unit test to upload files here; not sure yet how to do it though...
            }
        });
    }
    
}