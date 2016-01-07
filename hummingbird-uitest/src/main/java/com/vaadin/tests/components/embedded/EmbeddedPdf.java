package com.vaadin.tests.components.embedded;

import com.vaadin.server.ClassResource;
import com.vaadin.tests.components.TestBase;
import com.vaadin.ui.BrowserFrame;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;

public class EmbeddedPdf extends TestBase {

    @Override
    protected String getTestDescription() {
        return "The embedded flash should have the movie parameter set to \"someRandomValue\" and an allowFullScreen parameter set to \"true\".";
    }

    @Override
    protected Integer getTicketNumber() {
        return 3367;
    }

    @Override
    public void setup() {
        final BrowserFrame player = new BrowserFrame();
        player.setWidth("400px");
        player.setHeight("300px");
        player.setSource(new ClassResource(getClass(), "test.pdf"));
        add(player);

        add(new Button("Remove pdf", new Button.ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                remove(player);
            }
        }));

    }

}
