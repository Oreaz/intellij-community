/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui.overhead;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Function;

/**
 * @author egor
 */
public class OverheadView extends BorderLayoutPanel implements Disposable {
  @NotNull private final DebugProcessImpl myProcess;

  static final EnabledColumnInfo ENABLED_COLUMN = new EnabledColumnInfo();
  static final NameColumnInfo NAME_COLUMN = new NameColumnInfo();

  private final TableView<OverheadProducer> myTable;
  private final ListTableModel<OverheadProducer> myModel;

  private final MergingUpdateQueue myUpdateQueue;

  public OverheadView(@NotNull DebugProcessImpl process) {
    myProcess = process;

    myModel = new ListTableModel<>(new ColumnInfo[]{
      ENABLED_COLUMN,
      NAME_COLUMN,
      new TimingColumnInfo("hits", s -> OverheadTimings.getHits(myProcess, s)),
      new TimingColumnInfo("time", s -> OverheadTimings.getTime(myProcess, s))},
                                   new ArrayList<>(OverheadTimings.getProducers(process)),
                                   3, SortOrder.DESCENDING);
    myModel.setSortable(true);
    myTable = new TableView<>(myModel);
    addToCenter(ScrollPaneFactory.createScrollPane(myTable));
    TableUtil.setupCheckboxColumn(myTable.getColumnModel().getColumn(0));

    myUpdateQueue = new MergingUpdateQueue("OverheadView", 500, true, null, this);

    OverheadTimings.addListener(o -> myUpdateQueue.queue(new Update(o) {
      @Override
      public void run() {
        int idx = myModel.indexOf(o);
        if (idx != -1) {
          myModel.fireTableRowsUpdated(idx, idx);
          return;
        }
        myModel.setItems(new ArrayList<>(OverheadTimings.getProducers(process)));
      }
    }), process);

    new DumbAwareAction("Toggle") {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(myTable.getSelectedRowCount() == 1);
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        myTable.getSelection().forEach(c -> c.setEnabled(!c.isEnabled()));
        myTable.repaint();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myTable);
  }

  private static class EnabledColumnInfo extends ColumnInfo<OverheadProducer, Boolean> {
    public EnabledColumnInfo() {
      super("");
    }

    @Override
    public Class<?> getColumnClass() {
      return Boolean.class;
    }

    @Nullable
    @Override
    public Boolean valueOf(OverheadProducer item) {
      return item.isEnabled();
    }

    @Override
    public boolean isCellEditable(OverheadProducer item) {
      return true;
    }

    @Override
    public void setValue(OverheadProducer item, Boolean value) {
      item.setEnabled(value);
    }
  }

  private static class NameColumnInfo extends ColumnInfo<OverheadProducer, OverheadProducer> {
    public NameColumnInfo() {
      super("name");
    }

    @Nullable
    @Override
    public OverheadProducer valueOf(OverheadProducer aspects) {
      return aspects;
    }

    @Override
    public Class<?> getColumnClass() {
      return OverheadProducer.class;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(OverheadProducer producer) {
      return new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
          if (value instanceof OverheadProducer) {
            OverheadProducer overheadProducer = (OverheadProducer)value;
            if (!overheadProducer.isEnabled()) {
              SimpleColoredComponent component = new SimpleColoredComponent();
              overheadProducer.customizeRenderer(component);
              component.iterator().forEachRemaining(f -> append(f, SimpleTextAttributes.GRAYED_ATTRIBUTES));
              setIcon(component.getIcon());
            }
            else {
              overheadProducer.customizeRenderer(this);
            }
          }
          setTransparentIconBackground(true);
        }
      };
    }
  }

  private static class TimingColumnInfo extends ColumnInfo<OverheadProducer, OverheadProducer> {
    private final Function<OverheadProducer, Long> myGetter;

    public TimingColumnInfo(@NotNull String name, Function<OverheadProducer, Long> getter) {
      super(name);
      myGetter = getter;
    }

    @Nullable
    @Override
    public OverheadProducer valueOf(OverheadProducer aspects) {
      return aspects;
    }

    @Override
    public Class<?> getColumnClass() {
      return OverheadProducer.class;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(OverheadProducer producer) {
      return new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table,
                                             @Nullable Object value,
                                             boolean selected,
                                             boolean hasFocus,
                                             int row,
                                             int column) {
          if (value instanceof OverheadProducer) {
            OverheadProducer overheadProducer = (OverheadProducer)value;
            append(String.valueOf(myGetter.apply(overheadProducer)),
                   overheadProducer.isEnabled() ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      };
    }

    @Nullable
    @Override
    public Comparator<OverheadProducer> getComparator() {
      return Comparator.comparing(c -> {
        Long value = myGetter.apply(c);
        return value != null ? value : Long.MAX_VALUE;
      });
    }
  }

  @Override
  public void dispose() {
  }
}
