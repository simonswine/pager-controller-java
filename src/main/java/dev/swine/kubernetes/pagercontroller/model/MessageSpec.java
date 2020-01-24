/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package dev.swine.kubernetes.pagercontroller.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.strimzi.crdgenerator.annotations.Description;
import io.strimzi.crdgenerator.annotations.OneOf;
import io.strimzi.crdgenerator.annotations.Minimum;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "channel", "text", "state"})
@EqualsAndHashCode
public class MessageSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private String text;
    private String channel;
    private MessageState state;

    @Description("Text defines the message to be sent")
    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Description("Channel contains the tlk.io channel to be used")
    public String getChannel() {
        return this.channel;
    }
    public void setChannel(String channel) {
        this.channel = channel;
    }

    @Description("State contains if the message has been sent or not")
    public MessageState getState() {
        return this.state;
    }
    public void setState(MessageState state) {
        this.state = state;
    }
}
