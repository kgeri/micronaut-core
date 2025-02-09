/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * <p>Allows a bean to specify that it replaces another bean. Note that the bean to be replaced cannot be
 * an {@link Infrastructure} bean.</p>
 *
 * @author Graeme Rocher
 * @see Infrastructure
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Replaces {

    String MEMBER_NAMED = "named";
    String MEMBER_FACTORY = "factory";
    String MEMBER_BEAN = "bean";
    String MEMBER_QUALIFIER = "qualifier";

    /**
     * @return The bean type that this bean replaces
     */
    @AliasFor(member = "bean")
    Class<?> value() default void.class;

    /**
     * @return The bean type that this bean replaces
     */
    Class<?> bean() default void.class;

    /**
     * @return The declaring bean type
     */
    Class<?> factory() default void.class;

    /**
     * The name of the qualifiers of the bean that should be replaced.
     *
     * @return The qualifier
     */
    Class<? extends Annotation> qualifier() default Annotation.class;

    /**
     * The name of the qualifiers of the bean that should be replaced.
     *
     * @return The qualifier
     */
    String named() default "";
}
