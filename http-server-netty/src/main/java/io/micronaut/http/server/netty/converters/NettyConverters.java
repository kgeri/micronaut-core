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
package io.micronaut.http.server.netty.converters;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Optional;

/**
 * Factory for bytebuf related converters.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
public class NettyConverters implements TypeConverterRegistrar {

    private final ConversionService conversionService;
    private final BeanProvider<MediaTypeCodecRegistry> decoderRegistryProvider;
    private final ChannelOptionFactory channelOptionFactory;

    /**
     * Default constructor.
     * @param conversionService The conversion service
     * @param decoderRegistryProvider The decoder registry provider
     * @param channelOptionFactory The decoder channel option factory
     */
    public NettyConverters(ConversionService conversionService,
                           //Prevent early initialization of the codecs
                           BeanProvider<MediaTypeCodecRegistry> decoderRegistryProvider,
                           ChannelOptionFactory channelOptionFactory) {
        this.conversionService = conversionService;
        this.decoderRegistryProvider = decoderRegistryProvider;
        this.channelOptionFactory = channelOptionFactory;
    }

    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                ChannelOption.class,
                (object, targetType, context) -> {
                    String str = object.toString();
                    String name = NameUtils.underscoreSeparate(str).toUpperCase(Locale.ENGLISH);
                    return Optional.of(channelOptionFactory.channelOption(name));
                }
        );

        conversionService.addConverter(
                ByteBuf.class,
                Object.class,
                byteBufToObjectConverter()
        );

        conversionService.addConverter(
                FileUpload.class,
                Object.class,
                fileUploadToObjectConverter()
        );

        conversionService.addConverter(
                NettyPartData.class,
                Object.class,
                nettyPartDataToObjectConverter()
        );

        conversionService.addConverter(
                Attribute.class,
                Object.class,
                nettyAttributeToObjectConverter()
        );

        conversionService.addConverter(
                String.class,
                ChannelOption.class,
                s -> channelOptionFactory.channelOption(NameUtils.environmentName(s))
        );
    }

    private TypeConverter<Attribute, Object> nettyAttributeToObjectConverter() {
        return (object, targetType, context) -> {
            try {
                final String value = object.getValue();
                if (targetType.isInstance(value)) {
                    return Optional.of(value);
                } else {
                    return conversionService.convert(value, targetType, context);
                }
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    private TypeConverter<NettyPartData, Object> nettyPartDataToObjectConverter() {
        return (object, targetType, context) -> {
            try {
                if (targetType.isAssignableFrom(ByteBuffer.class)) {
                    return Optional.of(object.getByteBuffer());
                } else if (targetType.isAssignableFrom(InputStream.class)) {
                    return Optional.of(object.getInputStream());
                } else {
                    ByteBuf byteBuf = object.getByteBuf();
                    try {
                        return conversionService.convert(byteBuf, targetType, context);
                    } finally {
                        byteBuf.release();
                    }
                }
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return A FileUpload to CompletedFileUpload converter
     */
    protected TypeConverter<FileUpload, Object> fileUploadToObjectConverter() {
        return (object, targetType, context) -> {
            try {
                if (!object.isCompleted()) {
                    return Optional.empty();
                }

                String contentType = object.getContentType();
                ByteBuf byteBuf = object.getByteBuf();
                if (StringUtils.isNotEmpty(contentType)) {
                    MediaType mediaType = MediaType.of(contentType);
                    Optional<MediaTypeCodec> registered = decoderRegistryProvider.get().findCodec(mediaType);
                    if (registered.isPresent()) {
                        MediaTypeCodec decoder = registered.get();
                        Object val = decoder.decode(targetType, new ByteBufInputStream(byteBuf));
                        return Optional.of(val);
                    } else {
                        return conversionService.convert(byteBuf, targetType, context);
                    }
                }
                return conversionService.convert(byteBuf, targetType, context);
            } catch (Exception e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return A converter that returns bytebufs to objects
     */
    protected TypeConverter<ByteBuf, Object> byteBufToObjectConverter() {
        return (object, targetType, context) -> conversionService.convert(object.toString(context.getCharset()), targetType, context);
    }
}
