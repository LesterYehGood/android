/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.network.details;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.profilers.ContentType;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.network.NetworkConnectionsModel;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.Payload;
import com.android.tools.profilers.stacktrace.DataViewer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.android.tools.profilers.ProfilerFonts.STANDARD_FONT;

/**
 * A view model which wraps a target {@link HttpData} and can create useful, shared UI components
 * and display values from it.
 */
final class HttpDataViewModel {
  private static final String ID_PAYLOAD_VIEWER = "PAYLOAD_VIEWER";
  private static final Border PAYLOAD_BORDER = new JBEmptyBorder(6, 0, 0, 0);
  private static final ImmutableSet<ContentType> REFORMAT_CODE_SUPPORTED_TYPES =
    ImmutableSet.of(ContentType.JSON, ContentType.HTML, ContentType.XML);

  private final NetworkConnectionsModel myModel;
  private final HttpData myHttpData;

  public HttpDataViewModel(@NotNull NetworkConnectionsModel model, @NotNull HttpData httpData) {
    myModel = model;
    myHttpData = httpData;
  }

  /**
   * Search for the payload {@link DataViewer} inside a component returned by
   * {@link #createBodyComponent(IdeProfilerComponents, ConnectionType)}. If this returns
   * {@code null}, that means no payload viewer was created for it, e.g. the http data
   * instance didn't have a payload and a "No data found" label was returned instead.
   */
  @VisibleForTesting
  @Nullable
  static JComponent findPayloadViewer(@Nullable JComponent body) {
    if (body == null) {
      return null;
    }
    return TabUiUtils.findComponentWithUniqueName(body, ID_PAYLOAD_VIEWER);
  }

  /**
   * Creates a component which displays the current {@link HttpData}'s headers as a list of
   * key/value pairs.
   */
  @NotNull
  public JComponent createHeaderComponent(@NotNull ConnectionType type) {
    return TabUiUtils.createStyledMapComponent(type.getHeader(myHttpData).getFields());
  }

  /**
   * Returns a title which should be shown above the body component created by
   * {@link #createBodyComponent(IdeProfilerComponents, ConnectionType)}.
   */
  @NotNull
  public String getBodyTitle(@NotNull ConnectionType type) {
    HttpData.Header header = type.getHeader(myHttpData);
    HttpData.ContentType contentType = header.getContentType();
    if (contentType.isEmpty()) {
      return "Body";
    }
    return String.format("Body ( %s )", getDisplayName(contentType));
  }

  /**
   * Returns a payload component which can display the underlying data of the current
   * {@link HttpData}'s {@link Payload}. If the payload is empty, this will return a label to
   * indicate that the target payload is not set. If the payload is not empty and is supported for
   * parsing, this will return a component containing both the raw data view and the parsed view.
   */
  @NotNull
  public JComponent createBodyComponent(@NotNull IdeProfilerComponents components, @NotNull ConnectionType type) {
    Payload payload = type.getPayload(myModel, myHttpData);
    if (payload.getBytes().isEmpty()) {
      return TabUiUtils.createHideablePanel(getBodyTitle(type), new JLabel("Not available"), null);
    }
    JComponent rawDataComponent = createRawDataComponent(payload, components);
    JComponent parsedDataComponent = createParsedDataComponent(payload, components);

    JComponent bodyComponent = rawDataComponent;
    JComponent northEastComponent = null;
    if (parsedDataComponent != null) {
      final CardLayout cardLayout = new CardLayout();
      final JPanel payloadPanel = new JPanel(cardLayout);
      String cardViewParsed = "View Parsed";
      String cardViewSource = "View Source";
      parsedDataComponent.setName(cardViewParsed);
      rawDataComponent.setName(cardViewSource);
      payloadPanel.add(parsedDataComponent, cardViewParsed);
      payloadPanel.add(rawDataComponent, cardViewSource);
      bodyComponent = payloadPanel;

      final JLabel toggleLabel = new JLabel(cardViewSource);
      northEastComponent = toggleLabel;
      Color toggleHoverColor = AdtUiUtils.overlayColor(toggleLabel.getBackground().getRGB(), toggleLabel.getForeground().getRGB(), 0.9f);
      Color toggleDefaultColor = AdtUiUtils.overlayColor(toggleLabel.getBackground().getRGB(), toggleHoverColor.getRGB(), 0.6f);
      toggleLabel.setForeground(toggleDefaultColor);
      toggleLabel.setFont(STANDARD_FONT);
      toggleLabel.setBorder(new JBEmptyBorder(0, 10, 0, 5));
      toggleLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          toggleLabel.setText(cardViewSource.equals(toggleLabel.getText()) ? cardViewParsed : cardViewSource);
          cardLayout.next(payloadPanel);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          toggleLabel.setForeground(toggleHoverColor);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          toggleLabel.setForeground(toggleDefaultColor);
        }
      });
    }
    bodyComponent.setName(type.getBodyComponentId());
    return TabUiUtils.createHideablePanel(getBodyTitle(type), bodyComponent, northEastComponent);
  }

  /**
   * Creates the raw data view of given {@link Payload}, assumed the payload is not empty.
   */
  @NotNull
  private static JComponent createRawDataComponent(@NotNull Payload payload, @NotNull IdeProfilerComponents components) {
    ContentType contentType = ContentType.fromMimeType(payload.getContentType().getMimeType());
    // TODO(b/111835527): Investigate the raw payload viewer for different content types.
    if (REFORMAT_CODE_SUPPORTED_TYPES.contains(contentType) || payload.getContentType().isFormData()) {
      JTextArea textArea = new JTextArea(payload.getBytes().toStringUtf8());
      textArea.setLineWrap(true);
      textArea.setFont(ProfilerFonts.H4_FONT);
      textArea.setEditable(false);
      textArea.setBackground(null);
      return textArea;
    }

    DataViewer viewer = components.createDataViewer(payload.getBytes().toByteArray(), contentType, DataViewer.Style.RAW);
    JComponent viewerComponent = viewer.getComponent();
    viewerComponent.setName(ID_PAYLOAD_VIEWER);
    // We force a minimum height to make sure that the component always looks reasonable -
    // useful, for example, when we are displaying a bunch of text.
    int minimumHeight = 300;
    int originalHeight = viewer.getDimension() != null ? (int)viewer.getDimension().getHeight() : minimumHeight;
    viewerComponent.setMinimumSize(new Dimension(1, Math.min(minimumHeight, originalHeight)));
    viewerComponent.setBorder(PAYLOAD_BORDER);
    JComponent payloadComponent = new JPanel(new TabularLayout("*"));
    payloadComponent.add(viewerComponent, new TabularLayout.Constraint(0, 0));
    return payloadComponent;
  }

  /**
   * Creates the parsed data view of given {@link Payload}, or returns null if the payload is not applicable for parsing.
   * Assumed the payload is not empty.
   */
  @Nullable
  private static JComponent createParsedDataComponent(@NotNull Payload payload, @NotNull IdeProfilerComponents components) {
    if (payload.getContentType().isFormData()) {
      String contentToParse = payload.getBytes().toStringUtf8();
      final Map<String, String> parsedContent = new LinkedHashMap<>();
      Stream<String[]> parsedContentStream = Arrays.stream(contentToParse.trim().split("&")).map(s -> s.split("=", 2));
      parsedContentStream.forEach(a -> parsedContent.put(a[0], a.length > 1 ? a[1] : ""));
      return TabUiUtils.createStyledMapComponent(parsedContent);
    }
    ContentType contentType = ContentType.fromMimeType(payload.getContentType().getMimeType());
    if (REFORMAT_CODE_SUPPORTED_TYPES.contains(contentType)) {
      DataViewer viewer = components.createDataViewer(payload.getBytes().toByteArray(), contentType, DataViewer.Style.PRETTY);
      // Returns the successfully formatted view, if failed, ignores the returned component.
      if (viewer.getStyle() == DataViewer.Style.PRETTY) {
        viewer.getComponent().setBorder(PAYLOAD_BORDER);
        return viewer.getComponent();
      }
    }
    return null;
  }

  /**
   * Returns a user visible display name that represents the target {@code contentType}, with the
   * first letter capitalized.
   */
  @VisibleForTesting
  static String getDisplayName(@NotNull HttpData.ContentType contentType) {
    String mimeType = contentType.getMimeType().trim();
    if (mimeType.isEmpty()) {
      return mimeType;
    }
    if (contentType.isFormData()) {
      return "Form Data";
    }
    String[] typeAndSubType = mimeType.split("/", 2);
    boolean showSubType = typeAndSubType.length > 1 && (typeAndSubType[0].equals("text") || typeAndSubType[0].equals("application"));
    String name = showSubType ? typeAndSubType[1] : typeAndSubType[0];
    if (name.isEmpty() || showSubType) {
      return name.toUpperCase();
    }
    return name.substring(0, 1).toUpperCase() + name.substring(1);
  }


  public enum ConnectionType {
    REQUEST,
    RESPONSE;

    @NotNull
    HttpData.Header getHeader(@NotNull HttpData data) {
      return (this == REQUEST) ? data.getRequestHeader() : data.getResponseHeader();
    }

    @NotNull
    Payload getPayload(@NotNull NetworkConnectionsModel model, @NotNull HttpData data) {
      return (this == REQUEST) ? Payload.newRequestPayload(model, data) : Payload.newResponsePayload(model, data);
    }

    @NotNull
    String getBodyComponentId() {
      return (this == REQUEST) ? "REQUEST_PAYLOAD_COMPONENT" : "RESPONSE_PAYLOAD_COMPONENT";
    }
  }
}
