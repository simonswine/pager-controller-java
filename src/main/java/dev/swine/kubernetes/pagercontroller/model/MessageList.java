package dev.swine.kubernetes.pagercontroller.model;

import io.fabric8.kubernetes.client.CustomResourceList;

public class MessageList extends CustomResourceList<Message> {
    @Override
    public String getApiVersion() {
        return "v12";
    }

    @Override
    public void setApiVersion(String apiVersion) {
    }
}
