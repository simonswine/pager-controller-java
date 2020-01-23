/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package dev.swine.kubernetes.pagercontroller.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.strimzi.crdgenerator.annotations.Description;
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
        "replicas", "image", "bootstrapServers", "tls", "authentication", "http", "consumer",
        "producer", "resources", "jvmOptions", "logging",
        "metrics", "livenessProbe", "readinessProbe", "template", "tracing"})
@EqualsAndHashCode
public class MessageSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private int replicas;

    @Description("The number of pods in the `Deployment`.")
    @Minimum(0)
    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }
}
