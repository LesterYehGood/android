// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.jcip.annotations.NotThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Composite version of {@link ResolvingConverter}.
 */
public class CompositeConverter extends ResolvingConverter<String> {
  private final List<ResolvingConverter<String>> myConverters;

  /**
   * Constructor is not public, use {@link Builder} to create an instance of {@link ResolvingConverter}
   * that might (or might not) be a {@link CompositeConverter}
   */
  CompositeConverter(List<ResolvingConverter<String>> converters) {
    myConverters = converters;
  }

  @Override
  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    List<String> variants = new ArrayList<>();
    for (ResolvingConverter<String> converter : myConverters) {
      variants.addAll(converter.getVariants(context));
    }
    return variants;
  }

  @Override
  public String fromString(@Nullable String s, ConvertContext context) {
    // Returning non-null value from this method means that the value is valid.
    // To ensure that, we check all the containing converters to make sure at least
    // one of those recognized passed value.
    for (ResolvingConverter<String> converter : myConverters) {
      String result = converter.fromString(s, context);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  /**
   * Helper class for building a composite {@link ResolvingConverter}.
   */
  @NotThreadSafe
  public static class Builder {
    /**
     * List that stores converters added via {@link #addConverter(ResolvingConverter)}.
     * Method {@link #build()} passes it to CompositeConverter directly without defensive
     * copying, which is justified by ensuring it would be called only once.
     */
    private final List<ResolvingConverter<String>> myConverters = new ArrayList<>();
    private boolean myIsBuilt = false;

    private void assertNotBuilt() {
      if (myIsBuilt) {
        throw new IllegalStateException("CompositeConverterBuilder shouldn't be used after .build() is called");
      }
    }

    public void addConverter(@NotNull ResolvingConverter<String> converter) {
      assertNotBuilt();
      myConverters.add(converter);
    }

    /**
     * Build a composite {@link ResolvingConverter} which uses all added converters to this builder.
     * Has an optimization for cases when only zero or one converters were added and thus no
     * composite wrapper is required.
     * <p/>
     * After calling this method instance CompositeConverterBuilder shouldn't be used,
     * and invocations of all methods will throw an {@link IllegalStateException}
     */
    @Nullable
    public ResolvingConverter<String> build() {
      assertNotBuilt();
      myIsBuilt = true;

      switch (myConverters.size()) {
        case 0:
          return null;
        case 1:
          return myConverters.get(0);
        default:
          return new CompositeConverter(myConverters);
      }
    }
  }
}
