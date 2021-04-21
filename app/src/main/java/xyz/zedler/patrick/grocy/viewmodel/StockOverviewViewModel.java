package xyz.zedler.patrick.grocy.viewmodel;

/*
    This file is part of Grocy Android.

    Grocy Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Grocy Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Grocy Android.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2020-2021 by Patrick Zedler & Dominic Zedler
*/

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import com.android.volley.VolleyError;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.HorizontalFilterBarMulti;
import xyz.zedler.patrick.grocy.model.HorizontalFilterBarSingle;
import xyz.zedler.patrick.grocy.model.InfoFullscreen;
import xyz.zedler.patrick.grocy.model.Location;
import xyz.zedler.patrick.grocy.model.MissingItem;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.ProductGroup;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.model.ShoppingListItem;
import xyz.zedler.patrick.grocy.model.StockItem;
import xyz.zedler.patrick.grocy.repository.StockOverviewRepository;
import xyz.zedler.patrick.grocy.util.Constants;

public class StockOverviewViewModel extends BaseViewModel {

    private static final String TAG = ShoppingListViewModel.class.getSimpleName();

    private final SharedPreferences sharedPrefs;
    private final DownloadHelper dlHelper;
    private final GrocyApi grocyApi;
    private final StockOverviewRepository repository;

    private final MutableLiveData<Boolean> isLoadingLive;
    private final MutableLiveData<InfoFullscreen> infoFullscreenLive;
    private final MutableLiveData<Boolean> offlineLive;
    private final MutableLiveData<ArrayList<StockItem>> filteredStockItemsLive;

    private ArrayList<StockItem> stockItems;
    private ArrayList<Product> products;
    private HashMap<Integer, Product> productHashMap;
    private ArrayList<ShoppingListItem> shoppingListItems;
    private ArrayList<String> shoppingListItemsProductIds;
    private ArrayList<Location> locations;
    private ArrayList<ProductGroup> productGroups;
    private ArrayList<QuantityUnit> quantityUnits;
    private HashMap<Integer, QuantityUnit> quantityUnitHashMap;
    private ArrayList<StockItem> dueItemsTemp;
    private ArrayList<StockItem> overdueItemsTemp;
    private ArrayList<StockItem> expiredItemsTemp;
    private ArrayList<MissingItem> missingItems;
    private ArrayList<StockItem> missingStockItems;
    private HashMap<Integer, MissingItem> productIdsMissingItems;

    private DownloadHelper.Queue currentQueueLoading;
    private String searchInput;
    private HorizontalFilterBarSingle horizontalFilterBarSingle;
    private HorizontalFilterBarMulti horizontalFilterBarMulti;
    private int itemsDueCount;
    private int itemsOverdueCount;
    private int itemsExpiredCount;
    private int itemsMissingCount;
    private int itemsInStockCount;
    private final boolean debug;

    public StockOverviewViewModel(@NonNull Application application) {
        super(application);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
        debug = sharedPrefs.getBoolean(Constants.PREF.DEBUG, false);

        isLoadingLive = new MutableLiveData<>(false);
        dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue);
        grocyApi = new GrocyApi(getApplication());
        repository = new StockOverviewRepository(application);

        infoFullscreenLive = new MutableLiveData<>();
        offlineLive = new MutableLiveData<>(false);
        filteredStockItemsLive = new MutableLiveData<>();

        horizontalFilterBarSingle = new HorizontalFilterBarSingle(
                this::updateFilteredStockItems,
                HorizontalFilterBarSingle.DUE_NEXT,
                HorizontalFilterBarSingle.OVERDUE,
                HorizontalFilterBarSingle.EXPIRED,
                HorizontalFilterBarSingle.MISSING,
                HorizontalFilterBarSingle.IN_STOCK
        );
        itemsDueCount = 0;
        itemsOverdueCount = 0;
        itemsExpiredCount = 0;
        itemsMissingCount = 0;
        itemsInStockCount = 0;
        horizontalFilterBarMulti = new HorizontalFilterBarMulti(
                this::updateFilteredStockItems
        );
    }

    public void loadFromDatabase(boolean downloadAfterLoading) {
        repository.loadFromDatabase(
                (quantityUnits, productGroups, stockItems, products, missingItems, missingStockItems, shoppingListItems, locations) -> {
                    this.quantityUnits = quantityUnits;
                    quantityUnitHashMap = new HashMap<>();
                    for(QuantityUnit quantityUnit : quantityUnits) {
                        quantityUnitHashMap.put(quantityUnit.getId(), quantityUnit);
                    }
                    this.productGroups = productGroups;
                    this.products = products;
                    productHashMap = new HashMap<>();
                    for(Product product : products) {
                        productHashMap.put(product.getId(), product);
                    }

                    itemsDueCount = 0;
                    itemsOverdueCount = 0;
                    itemsExpiredCount = 0;
                    itemsMissingCount = 0;
                    this.stockItems = stockItems;
                    for(StockItem stockItem : stockItems) {
                        stockItem.setProduct(productHashMap.get(stockItem.getProductId()));
                        if(stockItem.isItemDue()) itemsDueCount++;
                        if(stockItem.isItemOverdue()) itemsOverdueCount++;
                        if(stockItem.isItemExpired()) itemsExpiredCount++;
                    }

                    this.missingItems = missingItems;
                    itemsMissingCount = missingItems.size();
                    itemsInStockCount = stockItems.size();
                    for(MissingItem missingItem : missingItems) {
                        if(missingItem.getIsPartlyInStock() == 0) itemsInStockCount--;
                    }
                    productIdsMissingItems = new HashMap<>();
                    for(MissingItem item : missingItems) {
                        productIdsMissingItems.put(item.getId(), item);
                    }
                    this.missingStockItems = missingStockItems;
                    this.shoppingListItems = shoppingListItems;
                    shoppingListItemsProductIds = new ArrayList<>();
                    for(ShoppingListItem item : shoppingListItems) {
                        if(item.getProductId() != null && !item.getProductId().isEmpty()) {
                            shoppingListItemsProductIds.add(item.getProductId());
                        }
                    }
                    this.locations = locations;

                    updateFilteredStockItems();
                    if(downloadAfterLoading) downloadData();
                }
        );
    }

    public void downloadData(@Nullable String dbChangedTime) {
        if(currentQueueLoading != null) {
            currentQueueLoading.reset(true);
            currentQueueLoading = null;
        }
        if(isOffline()) { // skip downloading and update recyclerview
            isLoadingLive.setValue(false);
            updateFilteredStockItems();
            return;
        }
        if(dbChangedTime == null) {
            dlHelper.getTimeDbChanged(this::downloadData, () -> onDownloadError(null));
            return;
        }

        DownloadHelper.OnQueueEmptyListener onQueueEmptyListener = () -> {
            HashMap<Integer, StockItem> stockItemHashMap = new HashMap<>();
            for(StockItem stockItem : stockItems) {
                stockItemHashMap.put(stockItem.getProductId(), stockItem);
            }

            for(StockItem stockItemDue : dueItemsTemp) {
                StockItem stockItem = stockItemHashMap.get(stockItemDue.getProductId());
                if(stockItem == null) continue;
                stockItem.setItemDue(true);
            }
            for(StockItem stockItemOverdue : overdueItemsTemp) {
                StockItem stockItem = stockItemHashMap.get(stockItemOverdue.getProductId());
                if(stockItem == null) continue;
                stockItem.setItemOverdue(true);
            }
            for(StockItem stockItemExpired : expiredItemsTemp) {
                StockItem stockItem = stockItemHashMap.get(stockItemExpired.getProductId());
                if(stockItem == null) continue;
                stockItem.setItemExpired(true);
            }

            DownloadHelper.Queue queue = dlHelper.newQueue(this::onQueueEmpty, this::onDownloadError);
            missingStockItems = new ArrayList<>();
            for(MissingItem missingItem : missingItems) {
                StockItem missingStockItem = stockItemHashMap.get(missingItem.getId());
                if(missingStockItem != null) { // already downloaded
                    missingStockItems.add(missingStockItem);
                } else {
                    queue.append(dlHelper.getProductDetails(missingItem.getId(), productDetails -> {
                        StockItem stockItem = new StockItem(productDetails);
                        stockItems.add(stockItem);
                        missingStockItems.add(stockItem);
                    }));
                }
            }
            if(queue.getSize() == 0) {
                onQueueEmpty();
                return;
            }
            queue.start();
        };

        DownloadHelper.Queue queue = dlHelper.newQueue(onQueueEmptyListener, this::onDownloadError);
        queue.append(
                dlHelper.updateQuantityUnits(dbChangedTime, quantityUnits -> {
                    this.quantityUnits = quantityUnits;
                    quantityUnitHashMap = new HashMap<>();
                    for(QuantityUnit quantityUnit : quantityUnits) {
                        quantityUnitHashMap.put(quantityUnit.getId(), quantityUnit);
                    }
                }), dlHelper.updateProductGroups(dbChangedTime, productGroups -> {
                    this.productGroups = productGroups;
                    //setMenuProductGroupFilters();
                    //updateMenuFilterVisibility();
                }), dlHelper.updateStockItems(
                        dbChangedTime, stockItems -> this.stockItems = stockItems
                ), dlHelper.updateProducts(dbChangedTime, products -> {
                    this.products = products;
                    productHashMap = new HashMap<>();
                    for(Product product : products) {
                        productHashMap.put(product.getId(), product);
                    }
                }), dlHelper.updateVolatile(dbChangedTime, (due, overdue, expired, missing) -> {
                    this.dueItemsTemp = due;
                    itemsDueCount = due.size();
                    this.overdueItemsTemp = overdue;
                    itemsOverdueCount = overdue.size();
                    this.expiredItemsTemp = expired;
                    itemsExpiredCount = expired.size();
                    this.missingItems = missing;
                    itemsMissingCount = missing.size();
                    itemsInStockCount = stockItems.size();
                    for(MissingItem missingItem : missingItems) {
                        if(missingItem.getIsPartlyInStock() == 0) itemsInStockCount--;
                    }
                    productIdsMissingItems = new HashMap<>();
                    for(MissingItem item : missingItems) {
                        productIdsMissingItems.put(item.getId(), item);
                    }
                }), dlHelper.updateShoppingListItems(dbChangedTime, shoppingListItems -> {
                    this.shoppingListItems = shoppingListItems;
                    shoppingListItemsProductIds = new ArrayList<>();
                    for(ShoppingListItem item : shoppingListItems) {
                        if(item.getProductId() != null && !item.getProductId().isEmpty()) {
                            shoppingListItemsProductIds.add(item.getProductId());
                        }
                    }
                }), dlHelper.updateLocations(
                        dbChangedTime, locations -> this.locations = locations
                )
        );

        if(queue.isEmpty()) {
            onQueueEmpty();
            return;
        }

        currentQueueLoading = queue;
        queue.start();
    }

    public void downloadData() {
        downloadData(null);
    }

    public void downloadDataForceUpdate() {
        SharedPreferences.Editor editPrefs = sharedPrefs.edit();
        editPrefs.putString(Constants.PREF.DB_LAST_TIME_QUANTITY_UNITS, null);
        editPrefs.putString(Constants.PREF.DB_LAST_TIME_PRODUCT_GROUPS, null);
        editPrefs.putString(Constants.PREF.DB_LAST_TIME_STOCK_ITEMS, null);
        editPrefs.putString(Constants.PREF.DB_LAST_TIME_PRODUCTS, null);
        editPrefs.putString(Constants.PREF.DB_LAST_TIME_VOLATILE, null);
        editPrefs.putString(Constants.PREF.DB_LAST_TIME_SHOPPING_LIST_ITEMS, null);
        editPrefs.putString(Constants.PREF.DB_LAST_TIME_LOCATIONS, null);
        editPrefs.apply();
        downloadData();
    }

    private void onQueueEmpty() {
        repository.updateDatabase(
                this.quantityUnits,
                this.productGroups,
                this.stockItems,
                this.products,
                this.missingItems,
                this.missingStockItems,
                this.shoppingListItems,
                this.locations,
                this::updateFilteredStockItems
        );
    }

    private void onDownloadError(@Nullable VolleyError error) {
        if(debug) Log.e(TAG, "onError: VolleyError: " + error);
        showMessage(getString(R.string.msg_no_connection));
        if(!isOffline()) setOfflineLive(true);
    }

    public void updateFilteredStockItems() {
        ArrayList<StockItem> filteredStockItems = new ArrayList<>();

        for(StockItem item : this.stockItems) {
            boolean searchContainsItem = true;
            if(searchInput != null && !searchInput.isEmpty()) {
                searchContainsItem = item.getProduct().getName().toLowerCase().contains(searchInput);
            }
            if(!searchContainsItem) continue;

            if(horizontalFilterBarSingle.isNoFilterActive()
                    || horizontalFilterBarSingle.isFilterActive(HorizontalFilterBarSingle.DUE_NEXT)
                    && item.isItemDue()
                    || horizontalFilterBarSingle.isFilterActive(HorizontalFilterBarSingle.OVERDUE)
                    && item.isItemOverdue()
                    || horizontalFilterBarSingle.isFilterActive(HorizontalFilterBarSingle.EXPIRED)
                    && item.isItemExpired()
                    || horizontalFilterBarSingle.isFilterActive(HorizontalFilterBarSingle.MISSING)
                    && productIdsMissingItems.containsKey(item.getProductId())
                    || horizontalFilterBarSingle.isFilterActive(HorizontalFilterBarSingle.IN_STOCK)
                    && (!productIdsMissingItems.containsKey(item.getProductId())
                    || productIdsMissingItems.get(item.getProductId()).getIsPartlyInStock() == 1)
            ) filteredStockItems.add(item);
        }

        filteredStockItemsLive.setValue(filteredStockItems);
    }

    public boolean isSearchActive() {
        return searchInput != null && !searchInput.isEmpty();
    }

    public void resetSearch() {
        searchInput = null;
    }

    public MutableLiveData<ArrayList<StockItem>> getFilteredStockItemsLive() {
        return filteredStockItemsLive;
    }

    public int getItemsDueCount() {
        return itemsDueCount;
    }

    public int getItemsOverdueCount() {
        return itemsOverdueCount;
    }

    public int getItemsExpiredCount() {
        return itemsExpiredCount;
    }

    public int getItemsMissingCount() {
        return itemsMissingCount;
    }

    public int getItemsInStockCount() {
        return itemsInStockCount;
    }

    public void updateSearchInput(String input) {
        this.searchInput = input.toLowerCase();
        updateFilteredStockItems();
    }

    public void toggleDoneStatus(ShoppingListItem listItem) {
        if(listItem == null) {
            showErrorMessage();
            return;
        }
        ShoppingListItem shoppingListItem = listItem.getClone();

        if(shoppingListItem.getDoneSynced() == -1) {
            shoppingListItem.setDoneSynced(shoppingListItem.getDone());
        }

        shoppingListItem.setDone(shoppingListItem.getDone() == 0 ? 1 : 0);  // toggle state

        if(isOffline()) {
            updateDoneStatus(shoppingListItem);
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("done", shoppingListItem.getDone());
        } catch (JSONException e) {
            if(debug) Log.e(TAG, "toggleDoneStatus: " + e);
        }
        dlHelper.editShoppingListItem(
                shoppingListItem.getId(),
                body,
                response -> updateDoneStatus(shoppingListItem),
                error -> {
                    showMessage(getString(R.string.error_undefined));
                    if(debug) Log.e(TAG, "toggleDoneStatus: " + error);
                }
        ).perform(dlHelper.getUuid());
    }

    private void updateDoneStatus(ShoppingListItem shoppingListItem) {

    }

    public void addMissingItems() {
        /*ShoppingList shoppingList = getSelectedShoppingList();
        if(shoppingList == null) {
            showMessage(getString(R.string.error_undefined));
            return;
        }
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("list_id", getSelectedShoppingListId());
        } catch (JSONException e) {
            if(debug) Log.e(TAG, "setUpBottomMenu: add missing: " + e);
        }
        dlHelper.post(
                grocyApi.addMissingProducts(),
                jsonObject,
                response -> {
                    showMessage(getApplication().getString(
                            R.string.msg_added_missing_products,
                            shoppingList.getName()
                    ));
                    downloadData();
                },
                error -> {
                    showMessage(getString(R.string.error_undefined));
                    if(debug) Log.e(
                            TAG, "setUpBottomMenu: add missing "
                                    + shoppingList.getName()
                                    + ": " + error
                    );
                }
        );*/
    }

    public ArrayList<Integer> getProductIdsMissingItems() {
        return new ArrayList<>(productIdsMissingItems.keySet());
    }

    public ArrayList<String> getShoppingListItemsProductIds() {
        return shoppingListItemsProductIds;
    }

    public HorizontalFilterBarSingle getHorizontalFilterBarSingle() {
        return horizontalFilterBarSingle;
    }

    public HorizontalFilterBarMulti getHorizontalFilterBarMulti() {
        return horizontalFilterBarMulti;
    }

    public HashMap<Integer, Product> getProductHashMap() {
        return productHashMap;
    }

    public HashMap<Integer, QuantityUnit> getQuantityUnitHashMap() {
        return quantityUnitHashMap;
    }

    public QuantityUnit getQuantityUnitFromId(int id) {
        return quantityUnitHashMap.get(id);
    }

    @NonNull
    public MutableLiveData<Boolean> getOfflineLive() {
        return offlineLive;
    }

    public Boolean isOffline() {
        return offlineLive.getValue();
    }

    public void setOfflineLive(boolean isOffline) {
        offlineLive.setValue(isOffline);
    }

    @NonNull
    public MutableLiveData<Boolean> getIsLoadingLive() {
        return isLoadingLive;
    }

    @NonNull
    public MutableLiveData<InfoFullscreen> getInfoFullscreenLive() {
        return infoFullscreenLive;
    }

    public void setCurrentQueueLoading(DownloadHelper.Queue queueLoading) {
        currentQueueLoading = queueLoading;
    }

    public boolean isFeatureEnabled(String pref) {
        if(pref == null) return true;
        return sharedPrefs.getBoolean(pref, true);
    }

    @Override
    protected void onCleared() {
        dlHelper.destroy();
        super.onCleared();
    }
}
