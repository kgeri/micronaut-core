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
package io.micronaut.validation.validator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Singleton;

import javax.validation.ClockProvider;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.ParameterNameProvider;
import javax.validation.TraversableResolver;
import javax.validation.ValidatorContext;
import javax.validation.ValidatorFactory;

/**
 * Default validator factory implementation.
 *
 * @author graemerocher
 * @since 1.2.0
 */
@Requires(missingBeans = ValidatorFactory.class)
@Internal
@Singleton
public class DefaultValidatorFactory implements ValidatorFactory {

    private final ConversionService conversionService;
    private final Validator validator;
    private final ValidatorConfiguration configuration;

    /**
     * Default constructor.
     *
     * @param conversionService The conversion service
     * @param validator         The validator.
     * @param configuration     The configuration.
     */
    protected DefaultValidatorFactory(ConversionService conversionService,
                                      Validator validator,
                                      ValidatorConfiguration configuration) {
        this.conversionService = conversionService;
        this.validator = validator;
        this.configuration = configuration;
    }

    @Override
    public javax.validation.Validator getValidator() {
        return validator;
    }

    @Override
    public ValidatorContext usingContext() {
        return new DefaultValidatorConfiguration(conversionService);
    }

    @Override
    public MessageInterpolator getMessageInterpolator() {
        throw new UnsupportedOperationException("Method getMessageInterpolator() not supported");
    }

    @Override
    public TraversableResolver getTraversableResolver() {
        return configuration.getTraversableResolver();
    }

    @Override
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        throw new UnsupportedOperationException("Method getConstraintValidatorFactory() not supported");
    }

    @Override
    public ParameterNameProvider getParameterNameProvider() {
        throw new UnsupportedOperationException("Method getParameterNameProvider() not supported");
    }

    @Override
    public ClockProvider getClockProvider() {
        return configuration.getClockProvider();
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        throw new UnsupportedOperationException("Method unwrap(..) not supported");
    }

    @Override
    public void close() {
        // no-op
    }
}
