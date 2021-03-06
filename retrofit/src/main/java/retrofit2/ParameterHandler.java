/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Map;
import okhttp3.Headers;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import static retrofit2.Utils.checkNotNull;

abstract class ParameterHandler<T> {
  protected String name;
protected Headers headers;
protected String transferEncoding;

abstract void apply(RequestBuilder builder, T value) throws IOException;

  final ParameterHandler<Iterable<T>> iterable() {
    return new ParameterHandler<Iterable<T>>() {
      @Override void apply(RequestBuilder builder, Iterable<T> values) throws IOException {
        if (values == null) return; // Skip null values.

        for (T value : values) {
          ParameterHandler.this.apply(builder, value);
        }
      }
    };
  }

  final ParameterHandler<Object> array() {
    return new ParameterHandler<Object>() {
      @Override void apply(RequestBuilder builder, Object values) throws IOException {
        if (values == null) return; // Skip null values.

        for (int i = 0, size = Array.getLength(values); i < size; i++) {
          //noinspection unchecked
          ParameterHandler.this.apply(builder, (T) Array.get(values, i));
        }
      }
    };
  }

  static final class RelativeUrl extends ParameterHandler<Object> {
    @Override void apply(RequestBuilder builder, Object value) {
      builder.setRelativeUrl(value);
    }
  }

  static final class Header<T> extends ParameterHandler<T> {
    private final Converter<T, String> valueConverter;

    Header(String name, Converter<T, String> valueConverter) {
      name = checkNotNull(name, "name == null");
      this.valueConverter = valueConverter;
    }

    @Override void apply(RequestBuilder builder, T value) throws IOException {
      if (value == null) return; // Skip null values.
      builder.addHeaderToRequestBuilder(name, valueConverter.convert(value));
    }
  }

  static final class Path<T> extends ParameterHandler<T> {
    private final Converter<T, String> valueConverter;
    private final boolean encoded;

    Path(String name, Converter<T, String> valueConverter, boolean encoded) {
      name = checkNotNull(name, "name == null");
      this.valueConverter = valueConverter;
      this.encoded = encoded;
    }

    @Override void apply(RequestBuilder builder, T value) throws IOException {
      if (value == null) {
        throw new IllegalArgumentException(
            "Path parameter \"" + name + "\" value must not be null.");
      }
      builder.addPathParamNameAndValue(name, valueConverter.convert(value), encoded);
    }
  }

  static final class Query<T> extends ParameterHandler<T> {
    private final Converter<T, String> valueConverter;
    private final boolean encoded;

    Query(String name, Converter<T, String> valueConverter, boolean encoded) {
      name = checkNotNull(name, "name == null");
      this.valueConverter = valueConverter;
      this.encoded = encoded;
    }

    @Override void apply(RequestBuilder builder, T value) throws IOException {
      if (value == null) return; // Skip null values.
      builder.addQueryParamNameAndValue(name, valueConverter.convert(value), encoded);
    }
  }

  static final class QueryMap<T> extends ParameterHandler<Map<String, T>> {
    private final Converter<T, String> valueConverter;
    private final boolean encoded;

    QueryMap(Converter<T, String> valueConverter, boolean encoded) {
      this.valueConverter = valueConverter;
      this.encoded = encoded;
    }

    @Override void apply(RequestBuilder builder, Map<String, T> value) throws IOException {
      if (value == null) {
        throw new IllegalArgumentException("Query map was null.");
      }

      for (Map.Entry<String, T> entry : value.entrySet()) {
        String entryKey = entry.getKey();
        if (entryKey == null) {
          throw new IllegalArgumentException("Query map contained null key.");
        }
        T entryValue = entry.getValue();
        if (entryValue == null) {
          throw new IllegalArgumentException(
              "Query map contained null value for key '" + entryKey + "'.");
        }
        builder.addQueryParamNameAndValue(entryKey, valueConverter.convert(entryValue), encoded);
      }
    }
  }

  static final class Field<T> extends ParameterHandler<T> {
    private final Converter<T, String> valueConverter;
    private final boolean encoded;

    Field(String name, Converter<T, String> valueConverter, boolean encoded) {
      name = checkNotNull(name, "name == null");
      this.valueConverter = valueConverter;
      this.encoded = encoded;
    }

    @Override void apply(RequestBuilder builder, T value) throws IOException {
      if (value == null) return; // Skip null values.
      builder.addFormField(name, valueConverter.convert(value), encoded);
    }
  }

  static final class FieldMap<T> extends ParameterHandler<Map<String, T>> {
    private final Converter<T, String> valueConverter;
    private final boolean encoded;

    FieldMap(Converter<T, String> valueConverter, boolean encoded) {
      this.valueConverter = valueConverter;
      this.encoded = encoded;
    }

    @Override void apply(RequestBuilder builder, Map<String, T> value) throws IOException {
      if (value == null) {
        throw new IllegalArgumentException("Field map was null.");
      }

      for (Map.Entry<String, T> entry : value.entrySet()) {
        String entryKey = entry.getKey();
        if (entryKey == null) {
          throw new IllegalArgumentException("Field map contained null key.");
        }
        T entryValue = entry.getValue();
        if (entryValue == null) {
          throw new IllegalArgumentException(
              "Field map contained null value for key '" + entryKey + "'.");
        }
        builder.addFormField(entryKey, valueConverter.convert(entryValue), encoded);
      }
    }
  }

  static final class Part<T> extends ParameterHandler<T> {
    private final Converter<T, RequestBody> converter;

    Part(Headers headers, Converter<T, RequestBody> converter) {
      super.headers = headers;
      this.converter = converter;
    }

    @Override void apply(RequestBuilder builder, T value) {
      if (value == null) return; // Skip null values.

      createBody(builder, value);
    }

	private void createBody(RequestBuilder builder, T value) {
		RequestBody body;
		  try {
		    body = converter.convert(value);
		  } catch (IOException e) {
		    throw new RuntimeException("Unable to convert " + value + " to RequestBody", e);
		  }
		  builder.addPart(headers, body);
	}
  }

  static final class RawPart extends ParameterHandler<MultipartBody.Part> {
    static final RawPart INSTANCE = new RawPart();

    private RawPart() {
    }

    @Override void apply(RequestBuilder builder, MultipartBody.Part value) throws IOException {
      if (value != null) { // Skip null values.
        builder.addPart(value);
      }
    }
  }

  static final class PartMap<T> extends ParameterHandler<Map<String, T>> {
    private final Converter<T, RequestBody> valueConverter;
    PartMap(Converter<T, RequestBody> valueConverter, String transferEncoding) {
      this.valueConverter = valueConverter;
      super.transferEncoding = transferEncoding;
    }

    @Override void apply(RequestBuilder builder, Map<String, T> value) throws IOException {
      if (value == null) {
        throw new IllegalArgumentException("Part map was null.");
      }

      for (Map.Entry<String, T> entry : value.entrySet()) {
        String entryKey = entry.getKey();
        if (entryKey == null) {
          throw new IllegalArgumentException("Part map contained null key.");
        }
        T entryValue = entry.getValue();
        if (entryValue == null) {
          throw new IllegalArgumentException(
              "Part map contained null value for key '" + entryKey + "'.");
        }

        Headers headers = Headers.of(
            "Content-Disposition", "form-data; name=\"" + entryKey + "\"",
            "Content-Transfer-Encoding", transferEncoding);

        builder.addPart(headers, valueConverter.convert(entryValue));
      }
    }
  }

  static final class Body<T> extends ParameterHandler<T> {
    private final Converter<T, RequestBody> converter;

    Body(Converter<T, RequestBody> converter) {
      this.converter = converter;
    }

    @Override void apply(RequestBuilder builder, T value) {
      if (value == null) {
        throw new IllegalArgumentException("Body parameter value must not be null.");
      }
      createBody(builder, value);
    }

	private void createBody(RequestBuilder builder, T value) {
		RequestBody body;
		  try {
		    body = converter.convert(value);
		  } catch (IOException e) {
		    throw new RuntimeException("Unable to convert " + value + " to RequestBody", e);
		  }
		  builder.setBody(body);
	}
  }
}
