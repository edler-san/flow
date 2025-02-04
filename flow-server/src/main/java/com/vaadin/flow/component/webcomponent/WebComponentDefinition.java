/*
 * Copyright 2000-2018 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.flow.component.webcomponent;

import java.io.Serializable;

import com.vaadin.flow.component.Component;

import elemental.json.JsonValue;

/**
 * {@code WebComponentDefinition} defines the tag and properties of the
 * {@link Component} being exported as a web component.
 * <p>
 * Call the {@code addProperty (String, ?)} variants to add properties to the
 * web component.
 *
 * @param <C>
 *            type of the {@code component} being exported.
 */
public abstract class WebComponentDefinition<C extends Component>
        implements Serializable {

    protected abstract <P extends Serializable> PropertyConfiguration<C, P> addProperty(
            String name, Class<P> type, P defaultValue);

    /**
     * Add an {@code Integer} property to the exported web component identified
     * by {@code name}.
     *
     * @param name
     *            name of the property. While all formats are allowed, names in
     *            camelCase will be converted to dash-separated form, when
     *            property update events are generated, using form
     *            "property-name-changed", if the property is called
     *            "propertyName"
     * @param defaultValue
     *            default value of property.
     * @return fluent {@code PropertyConfiguration} for configuring the property
     */
    public PropertyConfiguration<C, Integer> addProperty(String name,
            int defaultValue) {
        return addProperty(name, Integer.class, defaultValue);
    }

    /**
     * Add an {@code Double} property to the exported web component identified
     * by {@code name}.
     *
     * @param name
     *            name of the property. While all formats are allowed, names in
     *            camelCase will be converted to dash-separated form, when
     *            property update events are generated, using form
     *            "property-name-changed", if the property is called
     *            "propertyName"
     * @param defaultValue
     *            default value of property.
     * @return fluent {@code PropertyConfiguration} for configuring the property
     */
    public PropertyConfiguration<C, Double> addProperty(String name,
            double defaultValue) {
        return addProperty(name, Double.class, defaultValue);
    }

    /**
     * Add an {@code String} property to the exported web component identified
     * by {@code name}.
     *
     * @param name
     *            name of the property. While all formats are allowed, names in
     *            camelCase will be converted to dash-separated form, when
     *            property update events are generated, using form
     *            "property-name-changed", if the property is called
     *            "propertyName"
     * @param defaultValue
     *            default value of property.
     * @return fluent {@code PropertyConfiguration} for configuring the property
     */
    public PropertyConfiguration<C, String> addProperty(String name,
            String defaultValue) {
        return addProperty(name, String.class, defaultValue);
    }

    /**
     * Add an {@code Boolean} property to the exported web component identified
     * by {@code name}.
     *
     * @param name
     *            name of the property. While all formats are allowed, names in
     *            camelCase will be converted to dash-separated form, when
     *            property update events are generated, using form
     *            "property-name-changed", if the property is called
     *            "propertyName"
     * @param defaultValue
     *            default value of property.
     * @return fluent {@code PropertyConfiguration} for configuring the property
     */
    public PropertyConfiguration<C, Boolean> addProperty(String name,
            boolean defaultValue) {
        return addProperty(name, Boolean.class, defaultValue);
    }

    /**
     * Add an {@code JsonValue} property to the exported web component
     * identified by {@code name}.
     *
     * @param name
     *            name of the property. While all formats are allowed, names in
     *            camelCase will be converted to dash-separated form, when
     *            property update events are generated, using form
     *            "property-name-changed", if the property is called
     *            "propertyName"
     * @param defaultValue
     *            default value of property.
     * @return fluent {@code PropertyConfiguration} for configuring the property
     */
    public PropertyConfiguration<C, JsonValue> addProperty(String name,
            JsonValue defaultValue) {
        return addProperty(name, JsonValue.class, defaultValue);
    }
}
