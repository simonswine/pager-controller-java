/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package dev.swine.kubernetes.pagercontroller.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.Map;

/**
 * Represents a generic status which can be used across different resources
 */
@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
@ToString
public abstract class Status implements Serializable {
    private long observedGeneration;
    private Map<String, Object> additionalProperties;

    @Description("The generation of the CRD that was last reconciled by the operator.")
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public long getObservedGeneration() {
        return observedGeneration;
    }

    public void setObservedGeneration(long observedGeneration) {
        this.observedGeneration = observedGeneration;
    }
}
