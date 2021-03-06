package com.walmartlabs.ollie.toml;

/*-
 * *****
 * Ollie
 * -----
 * Copyright (C) 2018 Takari
 * -----
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
 * =====
 */

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Stream;

public class Serializer {

  private static Stream<Entry<String, Object>> streamFields(final Object o) {
    if (o instanceof KeyGroup) return ((KeyGroup) o).entrySet().stream();

    return Arrays.stream(o.getClass().getFields())
        .filter(
            field -> {
              final int mods = field.getModifiers();
              return /* field.isAccessible() && */ Modifier.isPublic(mods)
                  && !Modifier.isStatic(mods);
            })
        .map(
            field -> {
              try {
                return new AbstractMap.SimpleEntry<String, Object>(field.getName(), field.get(o));
              } catch (final IllegalAccessException e) {
                throw new RuntimeException(
                    "checked if a field was accessible but it turned out not to be. should not happen",
                    e);
              }
            });
  }

  public static void serialize(Object o, Appendable out) throws IOException {
    serialize("", o, out);
  }

  public static void serialize(String path, Object o, Appendable out) throws IOException {
    Preconditions.checkNotNull(o);
    Preconditions.checkNotNull(out);
    // if not root object
    if (!Strings.isNullOrEmpty(path)) out.append('[').append(path).append("]\n");

    streamFields(o)
        .filter(
            field -> {
              return isTomlPrimitive(field.getValue());
            })
        .forEach(
            IOExceptionWrapper.consumer(
                field -> {
                  out.append(field.getKey()).append(" = ");
                  serializeValue(field.getValue(), out);
                  out.append('\n');
                }));

    streamFields(o)
        .filter(
            field -> {
              return !isTomlPrimitive(field.getValue());
            })
        .forEach(
            IOExceptionWrapper.consumer(
                field -> {
                  if (Strings.isNullOrEmpty(path)) serialize(field.getKey(), field.getValue(), out);
                  else serialize(path + "." + field.getKey(), field.getValue(), out);
                }));
  }

  public static void serialize(KeyGroup o, Appendable out) throws IOException {
    Preconditions.checkNotNull(o);
    Preconditions.checkNotNull(out);
    final String name = o.isRoot() ? null : o.getName();
    serialize(name, o, out);
  }

  private static void serializeDateTime(Temporal temporal, Appendable out) throws IOException {
    DateTimeFormatter.ISO_INSTANT.formatTo(temporal, out);
  }

  public static void serializeValue(Object obj, Appendable out) throws IOException {
    Preconditions.checkNotNull(obj);
    if (obj instanceof Calendar) {
      serializeDateTime(((Calendar) obj).toInstant(), out);
    } else if (obj instanceof Date) {
      serializeDateTime(((Date) obj).toInstant(), out);
    } else if (obj instanceof Temporal) {
      serializeDateTime((Temporal) obj, out);
    } else if (obj instanceof Iterable<?>) {
      out.append('[');
      final Iterable<?> iterable = (Iterable<?>) obj;
      for (final Iterator<?> it = iterable.iterator(); it.hasNext(); ) {
        serializeValue(it.next(), out);
        if (it.hasNext()) out.append(", ");
      }
      out.append(']');
    } else if (obj instanceof Object[]) {
      out.append('[');
      final Object[] arr = (Object[]) obj;
      for (int i = 0; i < arr.length; i++) {
        serializeValue(arr[i], out);
        if (i < arr.length - 1) out.append(", ");
      }
      out.append(']');
    } else if (obj.getClass().isArray()) {
      // handle primitive array separately as they cannot be cast to
      // Object[]
      out.append('[');
      for (int i = 0; i < Integer.MAX_VALUE; i++) {
        try {
          final Object element = Array.get(obj, i);
          serializeValue(element, out);
        } catch (final IndexOutOfBoundsException e) {
          break;
        }
        try {
          Array.get(obj, i + 1);
        } catch (final IndexOutOfBoundsException e) {
          continue;
        }
        out.append(", ");
      }
      out.append(']');
    } else if (obj instanceof CharSequence) {
      out.append('"');
      out.append(StringEscapeUtils.escapeJava(obj.toString()));
      out.append('"');
    } else if (obj instanceof Number || obj instanceof Boolean) {
      out.append(obj.toString());
    } else {
      throw new IllegalArgumentException(obj.getClass() + " cannot be serialized");
    }
  }

  /**
   * @return true if the object is of a type that may be the right-hand side of an assignment in
   *     toml. Currently this means, if it's not one of Long, Double, String, Calendar, Iterables
   *     except Maps, which are handled like objects
   */
  public static boolean isTomlPrimitive(Object o) {
    /*
     * regarding numbers, what I really would like to check is: if it is a
     * floating point number: check if it's within the bounds of double if
     * it is an integer: check if it's within the bounds of a signed long
     *
     * problem: How do I generically find out whether a child class of
     * Number is fixed or floating? all they have in common are conversion
     * methods like intValue longValue...
     *
     * <s>idea: call longValue & doubleValue and check for equality.</s>
     * doesn't work
     */
    return (o instanceof Number)
        || (o instanceof CharSequence)
        || (o instanceof Calendar)
        || (o instanceof Boolean)
        || ((o instanceof Iterable) && !((o instanceof Map)))
        || (o.getClass().isArray());
  }
}
