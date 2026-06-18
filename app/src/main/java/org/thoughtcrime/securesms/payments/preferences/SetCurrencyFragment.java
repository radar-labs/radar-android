package org.thoughtcrime.securesms.payments.preferences;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.BaseSettingsAdapter;
import org.thoughtcrime.securesms.components.settings.SettingHeader;
import org.thoughtcrime.securesms.components.settings.SingleSelectSetting;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;

import java.util.Currency;
import java.util.Locale;

public final class SetCurrencyFragment extends LoggingFragment {

  private boolean             handledInitialScroll = false;
  private @NonNull String     searchQuery          = "";
  private @Nullable MappingModelList lastUnfilteredItems;
  private @Nullable BaseSettingsAdapter adapter;
  private @Nullable RecyclerView listView;

  public SetCurrencyFragment() {
    super(R.layout.set_currency_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar      toolbar = view.findViewById(R.id.set_currency_fragment_toolbar);
    RecyclerView list    = view.findViewById(R.id.set_currency_fragment_list);
    listView = list;

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());
    setupSearchMenu(toolbar);

    SetCurrencyViewModel viewModel = new ViewModelProvider(this, new SetCurrencyViewModel.Factory()).get(SetCurrencyViewModel.class);

    adapter = new BaseSettingsAdapter();
    adapter.configureSingleSelect(selection -> viewModel.select((Currency) selection));
    list.setAdapter(adapter);

    viewModel.getCurrencyListState().observe(getViewLifecycleOwner(), currencyListState -> {
      lastUnfilteredItems = currencyListState.getItems();
      adapter.submitList(applyFilter(lastUnfilteredItems), () -> {
        if (currencyListState.isLoaded()               &&
            currencyListState.getSelectedIndex() != -1 &&
            savedInstanceState == null                 &&
            !handledInitialScroll                       &&
            searchQuery.isEmpty())
        {
          handledInitialScroll = true;
          list.post(() -> list.scrollToPosition(currencyListState.getSelectedIndex()));
        }
      });
    });
  }

  /** Inflates and wires the toolbar SearchView (mirrors iOS UISearchController in nav bar). */
  private void setupSearchMenu(@NonNull Toolbar toolbar) {
    toolbar.inflateMenu(R.menu.payments_currency_picker_menu);
    MenuItem searchItem = toolbar.getMenu().findItem(R.id.payments_currency_picker_menu_search);

    // The shared symbol_search_24 drawable has a hardcoded black fill, so tint it with the
    // theme-aware on-surface color to match the navigation icon in both light and dark themes.
    Drawable icon = searchItem.getIcon();
    if (icon != null) {
      icon = DrawableCompat.wrap(icon.mutate());
      DrawableCompat.setTint(icon, ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurface));
      searchItem.setIcon(icon);
    }

    SearchView searchView = (SearchView) searchItem.getActionView();
    if (searchView == null) return;
    searchView.setQueryHint(getString(R.string.SearchToolbar_search));
    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override public boolean onQueryTextSubmit(String query) { return false; }
      @Override public boolean onQueryTextChange(String newText) {
        searchQuery = newText == null ? "" : newText.trim();
        if (adapter != null && lastUnfilteredItems != null) {
          adapter.submitList(applyFilter(lastUnfilteredItems));
        }
        return true;
      }
    });
  }

  /**
   * Filters the currency item list by the current search query. Matches against the row's
   * display text (which includes the flag-prefixed currency name) and its summary (the ISO
   * code). Section headers and progress rows pass through unchanged when a query is active
   * so the section structure stays visible; if a query is empty, the full list is returned.
   */
  private @NonNull MappingModelList applyFilter(@NonNull MappingModelList items) {
    if (searchQuery.isEmpty()) {
      return items;
    }
    String needle = searchQuery.toLowerCase(Locale.getDefault());
    MappingModelList filtered = new MappingModelList();
    for (MappingModel<?> item : items) {
      if (item instanceof SingleSelectSetting.Item) {
        SingleSelectSetting.Item ssi = (SingleSelectSetting.Item) item;
        String text    = ssi.getText() == null ? "" : ssi.getText().toLowerCase(Locale.getDefault());
        String summary = ssi.getSummaryText() == null ? "" : ssi.getSummaryText().toLowerCase(Locale.getDefault());
        if (text.contains(needle) || summary.contains(needle)) {
          filtered.add(item);
        }
      } else if (item instanceof SettingHeader.Item) {
        // Keep the section header so the user still sees the grouping while searching.
        filtered.add(item);
      }
      // SettingProgress.Item is omitted while filtering — the user expects search results,
      // not a "still loading" spinner row mixed in.
    }
    return filtered;
  }
}
