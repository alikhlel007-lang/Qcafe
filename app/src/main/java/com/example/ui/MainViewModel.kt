package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ai.AiActionType
import com.example.data.ai.AiAssistantUiState
import com.example.data.ai.MAX_DAILY_AI_REQUESTS
import com.example.data.ai.MenuAiAssistant
import com.example.data.local.CafeDatabase
import com.example.data.model.AppUpdateConfig
import com.example.data.model.CafeTable
import com.example.data.model.CartItem
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.OrderRecord
import com.example.data.model.OrderStatus
import com.example.data.model.UserAccount
import com.example.data.repository.CafeRepository
import com.example.data.repository.AuthResult
import com.example.util.ImageCompressor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    SPLASH,
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
    CASHIER_ORDERS,
    MENU_MANAGEMENT,
    TABLES_BARCODE,
    CUSTOMER_MENU,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CafeRepository
    private val menuAiAssistant = MenuAiAssistant(application)

    init {
        val db = CafeDatabase.getDatabase(application, viewModelScope)
        repository = CafeRepository(db.cafeDao(), application)
        repository.startAppUpdateListener()
        viewModelScope.launch {
            try {
                val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (authUser != null && authUser.isEmailVerified) {
                    val uid = authUser.uid
                    val localUser = db.cafeDao().getUserByEmail(authUser.email ?: "")
                    val baseAccount = if (localUser != null) {
                        UserAccount(
                            id = uid,
                            fullName = localUser.fullName,
                            email = localUser.email,
                            cafeName = localUser.cafeName,
                            role = localUser.role
                        )
                    } else {
                        UserAccount(
                            id = uid,
                            fullName = authUser.displayName ?: "مدير الكافيه",
                            email = authUser.email ?: "",
                            cafeName = "Q-Cafe",
                            role = "owner"
                        )
                    }

                    // 1. Load persistent profile from SharedPreferences
                    val cachedProfile = repository.loadCafeProfile(uid)
                    val effectiveCafeName = cachedProfile["cafeName"] ?: baseAccount.cafeName
                    val effectiveOwnerName = cachedProfile["ownerName"] ?: baseAccount.fullName
                    val effectiveEmail = cachedProfile["email"] ?: baseAccount.email

                    val userAccount = baseAccount.copy(
                        cafeName = effectiveCafeName,
                        fullName = effectiveOwnerName,
                        email = effectiveEmail
                    )

                    _currentUser.value = userAccount
                    _cafeName.value = effectiveCafeName
                    _cafeOwnerName.value = effectiveOwnerName
                    _cafeEmail.value = effectiveEmail
                    cachedProfile["phone"]?.let { _cafePhone.value = it }
                    cachedProfile["address"]?.let { _cafeAddress.value = it }
                    cachedProfile["welcomeNote"]?.let { _cafeWelcomeNote.value = it }
                    cachedProfile["logoType"]?.let { _cafeLogoType.value = it }
                    cachedProfile["customLogoUri"]?.let { _cafeCustomLogoUri.value = it.ifBlank { null } }
                    cachedProfile["menuUrl"]?.let {
                        val resolved = resolveMenuUrl(it)
                        _webMenuUrl.value = resolved
                        repository.baseWebMenuUrl = resolved
                    }
                    cachedProfile["headerBannerUrl"]?.let { _cafeHeaderBannerUrl.value = it }

                    repository.currentCafeteriaId = uid
                    repository.isSystemShutdown.value = repository.loadSystemShutdownPref(uid)
                    repository.updateExistingTablesCafeteriaId()
                    repository.startFirestoreOrdersListener(viewModelScope, uid)
                    repository.startCafeteriaStatusListener(uid)
                    repository.loadMenuForCafeteria(uid)

                    // 2. Refresh profile from Firestore in background
                    launch(Dispatchers.IO) {
                        repository.fetchAndSyncCafeProfileFromFirestore(uid)?.let { remote ->
                            remote["cafeName"]?.let { if (it.isNotBlank()) _cafeName.value = it }
                            remote["phone"]?.let { if (it.isNotBlank()) _cafePhone.value = it }
                            remote["ownerName"]?.let { if (it.isNotBlank()) _cafeOwnerName.value = it }
                            remote["email"]?.let { if (it.isNotBlank()) _cafeEmail.value = it }
                            remote["address"]?.let { if (it.isNotBlank()) _cafeAddress.value = it }
                            remote["welcomeNote"]?.let { if (it.isNotBlank()) _cafeWelcomeNote.value = it }
                            remote["logoType"]?.let { if (it.isNotBlank()) _cafeLogoType.value = it }
                            remote["customLogoUri"]?.let { _cafeCustomLogoUri.value = it.ifBlank { null } }
                            remote["menuUrl"]?.let {
                                val resolved = resolveMenuUrl(it)
                                _webMenuUrl.value = resolved
                                repository.baseWebMenuUrl = resolved
                            }
                            remote["headerBannerUrl"]?.let { _cafeHeaderBannerUrl.value = it }
                        }
                    }
                }
            } catch (_: Exception) {}

            delay(1200)
            if (_currentScreen.value == AppScreen.SPLASH) {
                _currentScreen.value = if (_currentUser.value != null) AppScreen.CASHIER_ORDERS else AppScreen.LOGIN
            }
        }
    }

    // Navigation & Screen State
    private val _currentScreen = MutableStateFlow(AppScreen.SPLASH)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Current Logged In User
    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    // Real-time Cafeteria Suspension State (Live lock)
    val isCafeSuspended: StateFlow<Boolean> = repository.isCafeSuspended.asStateFlow()
    val isSystemShutdown: StateFlow<Boolean> = repository.isSystemShutdown.asStateFlow()
    val suspensionReason: StateFlow<String> = repository.cafeSuspensionReason.asStateFlow()
    val cafeMaxDebtLimit: StateFlow<Double> = repository.cafeMaxDebtLimit.asStateFlow()
    val cafeRemainingFee: StateFlow<Double> = repository.cafeRemainingFee.asStateFlow()
    val isOverDebtLimit: StateFlow<Boolean> = repository.isOverDebtLimit.asStateFlow()
    val cafeNotifications: StateFlow<List<com.example.data.model.CafeNotificationItem>> = repository.cafeNotifications.asStateFlow()
    val waiterCalls: StateFlow<List<com.example.data.model.WaiterCallItem>> = repository.waiterCalls.asStateFlow()
    val salesResetTimestamp: StateFlow<Long> = repository.salesResetTimestamp.asStateFlow()

    private val _dismissedUpdateVersionCode = MutableStateFlow(0)
    val visibleAppUpdate: StateFlow<AppUpdateConfig?> = combine(
        repository.appUpdateConfig,
        _dismissedUpdateVersionCode,
        _currentScreen
    ) { config, dismissedCode, screen ->
        if (config == null || screen == AppScreen.SPLASH) {
            null
        } else if (
            config.isUpdateActive &&
            config.downloadUrl.isNotBlank() &&
            config.latestVersionCode > BuildConfig.VERSION_CODE &&
            (config.isForceUpdate || dismissedCode < config.latestVersionCode)
        ) {
            config
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun dismissOptionalUpdate() {
        val code = repository.appUpdateConfig.value?.latestVersionCode ?: return
        _dismissedUpdateVersionCode.value = code
    }

    fun resolveWaiterCall(callId: String) {
        repository.resolveWaiterCall(callId)
    }

    fun resolveAllWaiterCalls() {
        repository.resolveAllWaiterCalls()
    }

    fun setSystemShutdown(shutdown: Boolean) {
        repository.setSystemShutdown(viewModelScope, shutdown)
    }

    fun resetSalesCounters() {
        repository.resetSalesCounters()
    }

    fun setNotificationPref(key: String, enabled: Boolean) {
        repository.setNotificationPref(key, enabled)
    }

    fun getNotificationPref(key: String, defaultVal: Boolean = true): Boolean {
        return repository.getNotificationPref(key, defaultVal)
    }

    fun refreshDebtAndNotifications() {
        val uid = _currentUser.value?.id ?: repository.currentCafeteriaId
        repository.checkDebtAndBuildNotifications(
            cafeId = uid,
            limit = repository.cafeMaxDebtLimit.value,
            isSuspended = repository.isCafeSuspended.value,
            suspendReason = repository.cafeSuspensionReason.value
        )
    }

    // Real-time Data Streams from DB
    val categories: StateFlow<List<MenuCategory>> = repository.categories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val menuItems: StateFlow<List<MenuItem>> = repository.menuItems.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val tables: StateFlow<List<CafeTable>> = repository.tables.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val orders: StateFlow<List<OrderRecord>> = repository.orders.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Customer Menu & Ordering State
    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _selectedTableNumber = MutableStateFlow(1)
    val selectedTableNumber: StateFlow<Int> = _selectedTableNumber.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Auth Forms State
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage: StateFlow<String?> = _authSuccessMessage.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _unverifiedEmail = MutableStateFlow<String?>(null)
    val unverifiedEmail: StateFlow<String?> = _unverifiedEmail.asStateFlow()

    private val _registrationSuccessMessage = MutableStateFlow<String?>(null)
    val registrationSuccessMessage: StateFlow<String?> = _registrationSuccessMessage.asStateFlow()

    // Cashier filter (default to NEW stage, "الكل" option removed)
    private val _orderStatusFilter = MutableStateFlow<OrderStatus>(OrderStatus.NEW)
    val orderStatusFilter: StateFlow<OrderStatus> = _orderStatusFilter.asStateFlow()

    // Cafe Profile & Branding Settings
    private val _cafeName = MutableStateFlow("Q-Cafe")
    val cafeName: StateFlow<String> = _cafeName.asStateFlow()

    private val _cafeOwnerName = MutableStateFlow("صاحب الكافتيريا")
    val cafeOwnerName: StateFlow<String> = _cafeOwnerName.asStateFlow()

    private val _cafePhone = MutableStateFlow("+964 780 000 0000")
    val cafePhone: StateFlow<String> = _cafePhone.asStateFlow()

    private val _cafeEmail = MutableStateFlow("cafe.owner@gmail.com")
    val cafeEmail: StateFlow<String> = _cafeEmail.asStateFlow()

    private val _cafeAddress = MutableStateFlow("الفرع الرئيسي - صالة العوائل والشباب")
    val cafeAddress: StateFlow<String> = _cafeAddress.asStateFlow()

    private val _cafeWelcomeNote = MutableStateFlow("أهلاً وسهلاً بكم في كافتيريتنا، نتمنى لكم أطيب الأوقات!")
    val cafeWelcomeNote: StateFlow<String> = _cafeWelcomeNote.asStateFlow()

    private val _cafeLogoType = MutableStateFlow("custom")
    val cafeLogoType: StateFlow<String> = _cafeLogoType.asStateFlow()

    private val _cafeCustomLogoUri = MutableStateFlow<String?>(null)
    val cafeCustomLogoUri: StateFlow<String?> = _cafeCustomLogoUri.asStateFlow()

    private val _cafeHeaderBannerUrl = MutableStateFlow("")
    val cafeHeaderBannerUrl: StateFlow<String> = _cafeHeaderBannerUrl.asStateFlow()

    private val _webMenuUrl = MutableStateFlow(repository.baseWebMenuUrl)
    val webMenuUrl: StateFlow<String> = _webMenuUrl.asStateFlow()

    private fun resolveMenuUrl(raw: String?): String {
        val clean = raw?.trim().orEmpty()
        return if (clean.isBlank() || clean.contains("alikhlel007-lang.github.io")) {
            "https://qcafe.online/?table="
        } else {
            clean
        }
    }

    fun setCustomLogoUri(uri: String?) {
        _cafeCustomLogoUri.value = uri
    }

    fun setHeaderBannerUrl(url: String) {
        _cafeHeaderBannerUrl.value = url
    }

    fun updateCafeProfile(
        name: String,
        phone: String,
        address: String = _cafeAddress.value,
        welcomeNote: String = _cafeWelcomeNote.value,
        logoType: String = _cafeLogoType.value,
        menuUrl: String = _webMenuUrl.value,
        ownerName: String = _cafeOwnerName.value,
        email: String = _cafeEmail.value,
        customLogoUri: String? = _cafeCustomLogoUri.value,
        headerBannerUrl: String = _cafeHeaderBannerUrl.value
    ) {
        _cafeName.value = name.ifBlank { "Q-Cafe" }
        _cafePhone.value = phone
        _cafeOwnerName.value = ownerName.ifBlank { "صاحب الكافتيريا" }
        _cafeEmail.value = email.ifBlank { "cafe.owner@gmail.com" }
        _cafeCustomLogoUri.value = customLogoUri
        _cafeHeaderBannerUrl.value = headerBannerUrl
        _cafeAddress.value = address
        _cafeWelcomeNote.value = welcomeNote
        _cafeLogoType.value = logoType
        _webMenuUrl.value = menuUrl.ifBlank { repository.baseWebMenuUrl }
        repository.baseWebMenuUrl = _webMenuUrl.value

        // Also update currentUser if available
        _currentUser.value?.let { current ->
            _currentUser.value = current.copy(
                cafeName = _cafeName.value,
                fullName = _cafeOwnerName.value,
                email = _cafeEmail.value
            )
        }

        // Persist profile settings permanently in SharedPreferences & Firestore
        val cafeId = _currentUser.value?.id ?: repository.currentCafeteriaId
        repository.saveCafeProfile(
            cafeId = cafeId,
            cafeName = _cafeName.value,
            phone = _cafePhone.value,
            ownerName = _cafeOwnerName.value,
            email = _cafeEmail.value,
            address = _cafeAddress.value,
            welcomeNote = _cafeWelcomeNote.value,
            logoType = _cafeLogoType.value,
            customLogoUri = _cafeCustomLogoUri.value,
            menuUrl = _webMenuUrl.value,
            themeColor = _themeAccentColorHex.value,
            headerBannerUrl = _cafeHeaderBannerUrl.value
        )
    }

    fun getCafeNumericId(): String {
        val cafeId = _currentUser.value?.id ?: repository.currentCafeteriaId
        return com.example.util.InvoiceSecurity.generateCafeNumericId(cafeId)
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        _authError.value = null
        _authSuccessMessage.value = null
        _unverifiedEmail.value = null
        _registrationSuccessMessage.value = null
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTableNumber(number: Int) {
        _selectedTableNumber.value = number
    }

    fun setOrderStatusFilter(status: OrderStatus) {
        _orderStatusFilter.value = status
    }

    // Cart Operations
    fun addToCart(item: MenuItem) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.menuItem.id == item.id }
        if (index != -1) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            current.add(CartItem(menuItem = item, quantity = 1))
        }
        _cart.value = current
    }

    fun removeFromCart(item: MenuItem) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.menuItem.id == item.id }
        if (index != -1) {
            val existing = current[index]
            if (existing.quantity > 1) {
                current[index] = existing.copy(quantity = existing.quantity - 1)
            } else {
                current.removeAt(index)
            }
        }
        _cart.value = current
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    // Submit order from table with Customer Name
    fun submitOrder(customerName: String, customerNotes: String, onSuccess: (String) -> Unit) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return

        viewModelScope.launch {
            val finalCustomerName = customerName.trim().ifBlank { "زبون طاولة ${_selectedTableNumber.value}" }
            val orderId = repository.createOrder(
                tableNumber = _selectedTableNumber.value,
                customerName = finalCustomerName,
                cartItems = cartItems,
                notes = customerNotes
            )
            clearCart()
            onSuccess(orderId)
        }
    }

    // Design & Theme Customization State
    private val _themeAccentColorHex = MutableStateFlow("#F59E0B")
    val themeAccentColorHex: StateFlow<String> = _themeAccentColorHex.asStateFlow()

    private val _cardCornerRadiusDp = MutableStateFlow(20)
    val cardCornerRadiusDp: StateFlow<Int> = _cardCornerRadiusDp.asStateFlow()

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    fun updateThemeAccentColor(hex: String) {
        _themeAccentColorHex.value = hex
    }

    fun updateCardCornerRadius(dp: Int) {
        _cardCornerRadiusDp.value = dp
    }

    fun updateFontScale(scale: Float) {
        _fontScale.value = scale
    }

    // Order status update in cashier
    fun updateOrderStatus(order: OrderRecord, newStatus: OrderStatus) {
        viewModelScope.launch {
            repository.updateOrderStatus(order.id, order, newStatus)
        }
    }

    fun deleteOrder(order: OrderRecord) {
        viewModelScope.launch {
            repository.deleteOrder(order)
        }
    }

    // Customer Menu Page Tab: 0 = Menu Orders, 1 = Offers & New Items, 2 = Games & Entertainment
    private val _customerMenuTab = MutableStateFlow(0)
    val customerMenuTab: StateFlow<Int> = _customerMenuTab.asStateFlow()

    fun setCustomerMenuTab(tab: Int) {
        _customerMenuTab.value = tab
    }

    // Categories & Items CRUD
    fun addCategory(nameAr: String, nameEn: String, iconType: String, customImageUri: String? = null) {
        viewModelScope.launch {
            repository.addCategory(nameAr, nameEn, iconType, customImageUri)
        }
    }

    fun updateCategory(category: MenuCategory) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: MenuCategory) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (_selectedCategoryId.value == category.id) {
                _selectedCategoryId.value = null
            }
        }
    }

    fun addMenuItem(
        categoryId: String,
        nameAr: String,
        nameEn: String,
        descriptionAr: String,
        descriptionEn: String,
        price: Double,
        currency: String = "د.ع",
        imageType: String = "burger",
        imageUri: String? = null,
        bgImageUri: String? = null,
        galleryImages: List<String> = emptyList(),
        isOffer: Boolean = false,
        offerDiscountPercent: Int = 0,
        offerTag: String = "عرض خاص"
    ) {
        viewModelScope.launch {
            repository.addMenuItem(
                categoryId = categoryId,
                nameAr = nameAr,
                nameEn = nameEn,
                descriptionAr = descriptionAr,
                descriptionEn = descriptionEn,
                price = price,
                currency = currency,
                imageType = imageType,
                imageUri = imageUri,
                bgImageUri = bgImageUri,
                galleryImages = galleryImages,
                isOffer = isOffer,
                offerDiscountPercent = offerDiscountPercent,
                offerTag = offerTag
            )
        }
    }

    fun updateMenuItem(item: MenuItem) {
        viewModelScope.launch {
            repository.updateMenuItem(item)
        }
    }

    fun toggleItemAvailability(item: MenuItem) {
        viewModelScope.launch {
            repository.updateMenuItem(item.copy(isAvailable = !item.isAvailable))
        }
    }

    fun deleteMenuItem(item: MenuItem) {
        viewModelScope.launch {
            repository.deleteMenuItem(item)
        }
    }

    // Scoped Cashier Menu AI Assistant (Free Tier via Firebase AI Logic)
    private val _aiUiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val aiUiState: StateFlow<AiAssistantUiState> = _aiUiState.asStateFlow()

    private val _aiDailyRemaining = MutableStateFlow(MAX_DAILY_AI_REQUESTS)
    val aiDailyRemaining: StateFlow<Int> = _aiDailyRemaining.asStateFlow()

    fun refreshAiQuota() {
        val cafeId = _currentUser.value?.id ?: repository.currentCafeteriaId
        _aiDailyRemaining.value = menuAiAssistant.getRemainingDailyQuota(cafeId)
    }

    fun resetAiState() {
        _aiUiState.value = AiAssistantUiState.Idle
        refreshAiQuota()
    }

    fun analyzeMenuWithAi(command: String, imageUriString: String?) {
        val cafeId = _currentUser.value?.id ?: repository.currentCafeteriaId
        refreshAiQuota()
        viewModelScope.launch {
            _aiUiState.value = AiAssistantUiState.Loading
            val result = menuAiAssistant.analyzeMenuRequest(
                cafeId = cafeId,
                userCommand = command,
                imageUriString = imageUriString,
                categories = categories.value,
                menuItems = menuItems.value
            )
            refreshAiQuota()
            result.fold(
                onSuccess = { (reply, actions) ->
                    _aiUiState.value = AiAssistantUiState.Success(
                        replyMessage = reply,
                        actions = actions,
                        attachedPhotoUri = imageUriString
                    )
                },
                onFailure = { error ->
                    _aiUiState.value = AiAssistantUiState.Error(
                        error.message ?: "تعذر تحليل الطلب عبر الذكاء الاصطناعي."
                    )
                }
            )
        }
    }

    fun toggleAiProposedAction(actionId: String) {
        val current = _aiUiState.value as? AiAssistantUiState.Success ?: return
        val updated = current.actions.map { action ->
            if (action.id == actionId) action.copy(isSelected = !action.isSelected) else action
        }
        _aiUiState.value = current.copy(actions = updated)
    }

    fun applySelectedAiActions(
        fallbackCategoryId: String,
        onComplete: (Int) -> Unit
    ) {
        val currentState = _aiUiState.value as? AiAssistantUiState.Success ?: return
        val selectedActions = currentState.actions.filter { it.isSelected }
        if (selectedActions.isEmpty()) return

        viewModelScope.launch {
            var appliedCount = 0
            val existingCategories = categories.value.toMutableList()
            val existingItems = menuItems.value
            val createdCategoriesByName = mutableMapOf<String, String>()

            val needsUploadedImage = selectedActions.any {
                it.actionType == AiActionType.ATTACH_UPLOADED_IMAGE || it.attachUploadedPhoto
            }
            val compressedPhotoBase64 = if (needsUploadedImage && !currentState.attachedPhotoUri.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    ImageCompressor.uriToCompressedBase64(getApplication(), currentState.attachedPhotoUri)
                }
            } else null

            // 1. Create any newly requested categories first
            selectedActions.filter { it.actionType == AiActionType.ADD_CATEGORY }.forEach { action ->
                val catNameAr = action.categoryNameAr?.trim().orEmpty()
                if (catNameAr.isNotBlank()) {
                    val existing = existingCategories.find { it.nameAr.trim().equals(catNameAr, ignoreCase = true) }
                    if (existing != null) {
                        createdCategoriesByName[catNameAr.lowercase()] = existing.id
                    } else {
                        val newCatId = repository.addCategoryAndReturnId(
                            nameAr = catNameAr,
                            nameEn = action.categoryNameEn?.trim()?.ifBlank { catNameAr } ?: catNameAr,
                            iconType = action.iconType?.ifBlank { "meals" } ?: "meals"
                        )
                        createdCategoriesByName[catNameAr.lowercase()] = newCatId
                        appliedCount++
                    }
                }
            }

            // 2. Apply item additions and updates
            selectedActions.filter { it.actionType != AiActionType.ADD_CATEGORY }.forEach { action ->
                when (action.actionType) {
                    AiActionType.ADD_ITEM -> {
                        val itemName = action.itemNameAr?.trim().orEmpty()
                        if (itemName.isNotBlank()) {
                            val targetCatName = action.categoryNameAr?.trim().orEmpty()
                            val resolvedCatId = when {
                                !action.categoryId.isNullOrBlank() && existingCategories.any { it.id == action.categoryId } ->
                                    action.categoryId
                                targetCatName.isNotBlank() && createdCategoriesByName.containsKey(targetCatName.lowercase()) ->
                                    createdCategoriesByName.getValue(targetCatName.lowercase())
                                targetCatName.isNotBlank() -> {
                                    val found = existingCategories.find { it.nameAr.trim().equals(targetCatName, ignoreCase = true) }
                                    if (found != null) {
                                        found.id
                                    } else {
                                        val autoCatId = repository.addCategoryAndReturnId(
                                            nameAr = targetCatName,
                                            nameEn = action.categoryNameEn?.trim()?.ifBlank { targetCatName } ?: targetCatName,
                                            iconType = action.iconType?.ifBlank { "meals" } ?: "meals"
                                        )
                                        createdCategoriesByName[targetCatName.lowercase()] = autoCatId
                                        autoCatId
                                    }
                                }
                                else -> fallbackCategoryId
                            }
                            repository.addMenuItem(
                                categoryId = resolvedCatId,
                                nameAr = itemName,
                                nameEn = action.itemNameEn?.trim()?.ifBlank { itemName } ?: itemName,
                                descriptionAr = action.descriptionAr?.trim().orEmpty(),
                                descriptionEn = action.descriptionAr?.trim().orEmpty(),
                                price = (action.price ?: 1000.0).coerceAtLeast(0.0),
                                currency = action.currency?.takeIf { it == "$" || it == "د.ع" } ?: "د.ع",
                                imageType = action.imageType?.ifBlank { "burger" } ?: "burger",
                                imageUri = if (action.attachUploadedPhoto) compressedPhotoBase64 else null,
                                isOffer = action.isOffer ?: false,
                                offerDiscountPercent = (action.offerDiscountPercent ?: 0).coerceIn(0, 99),
                                offerTag = action.offerTag?.ifBlank { "عرض خاص" } ?: "عرض خاص"
                            )
                            appliedCount++
                        }
                    }

                    AiActionType.UPDATE_PRICE -> {
                        val target = findMatchingMenuItem(existingItems, action.itemId, action.itemNameAr)
                        val newPrice = action.price
                        if (target != null && newPrice != null && newPrice >= 0.0) {
                            repository.updateMenuItem(
                                target.copy(
                                    price = newPrice,
                                    currency = action.currency?.takeIf { it == "$" || it == "د.ع" } ?: target.currency
                                )
                            )
                            appliedCount++
                        }
                    }

                    AiActionType.UPDATE_AVAILABILITY -> {
                        val target = findMatchingMenuItem(existingItems, action.itemId, action.itemNameAr)
                        val avail = action.isAvailable
                        if (target != null && avail != null) {
                            repository.updateMenuItem(target.copy(isAvailable = avail))
                            appliedCount++
                        }
                    }

                    AiActionType.UPDATE_DESCRIPTION -> {
                        val target = findMatchingMenuItem(existingItems, action.itemId, action.itemNameAr)
                        if (target != null) {
                            repository.updateMenuItem(
                                target.copy(
                                    nameAr = action.itemNameAr?.trim()?.ifBlank { target.nameAr } ?: target.nameAr,
                                    nameEn = action.itemNameEn?.trim()?.ifBlank { target.nameEn } ?: target.nameEn,
                                    descriptionAr = action.descriptionAr?.trim() ?: target.descriptionAr,
                                    descriptionEn = action.descriptionAr?.trim() ?: target.descriptionEn
                                )
                            )
                            appliedCount++
                        }
                    }

                    AiActionType.UPDATE_OFFER -> {
                        val target = findMatchingMenuItem(existingItems, action.itemId, action.itemNameAr)
                        if (target != null) {
                            val enableOffer = action.isOffer ?: true
                            val disc = (action.offerDiscountPercent ?: target.offerDiscountPercent).coerceIn(0, 99)
                            val updatedPrice = action.price?.takeIf { it >= 0.0 } ?: target.price
                            repository.updateMenuItem(
                                target.copy(
                                    isOffer = enableOffer,
                                    offerDiscountPercent = if (enableOffer) disc else 0,
                                    offerTag = if (enableOffer) (action.offerTag?.ifBlank { "عرض خاص" } ?: target.offerTag) else "",
                                    price = updatedPrice
                                )
                            )
                            appliedCount++
                        }
                    }

                    AiActionType.ATTACH_UPLOADED_IMAGE -> {
                        val target = findMatchingMenuItem(existingItems, action.itemId, action.itemNameAr)
                        if (target != null && !compressedPhotoBase64.isNullOrBlank()) {
                            val updatedItem = when (action.imageTarget?.lowercase()) {
                                "bg" -> target.copy(bgImageUri = compressedPhotoBase64)
                                "gallery" -> target.copy(galleryImages = (target.galleryImages + compressedPhotoBase64).distinct())
                                else -> target.copy(imageUri = compressedPhotoBase64)
                            }
                            repository.updateMenuItem(updatedItem)
                            appliedCount++
                        }
                    }

                    AiActionType.ADD_CATEGORY -> Unit
                }
            }

            _aiUiState.value = AiAssistantUiState.Idle
            onComplete(appliedCount)
        }
    }

    private fun findMatchingMenuItem(
        items: List<MenuItem>,
        itemId: String?,
        itemNameAr: String?
    ): MenuItem? {
        if (!itemId.isNullOrBlank()) {
            items.find { it.id == itemId }?.let { return it }
        }
        val cleanName = itemNameAr?.trim().orEmpty()
        if (cleanName.isNotBlank()) {
            items.find { it.nameAr.trim().equals(cleanName, ignoreCase = true) }?.let { return it }
            items.find { it.nameAr.contains(cleanName, ignoreCase = true) || cleanName.contains(it.nameAr, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    // Tables CRUD & Anti-fraud Barcode Regeneration
    fun addTable(number: Int, label: String, capacity: Int) {
        val cafeName = _currentUser.value?.cafeName ?: "CAFE"
        viewModelScope.launch {
            repository.addTable(number, label, capacity, cafeName)
        }
    }

    fun regenerateTableQr(table: CafeTable) {
        viewModelScope.launch {
            repository.regenerateTableQr(table)
        }
    }

    fun regenerateAllTablesQr() {
        viewModelScope.launch {
            repository.regenerateAllTablesQr(tables.value)
        }
    }

    fun generateDefaultTables(count: Int = 8) {
        viewModelScope.launch {
            repository.generateInitialTables(count)
        }
    }

    fun deleteTable(table: CafeTable) {
        viewModelScope.launch {
            repository.deleteTable(table)
        }
    }

    // Cashier reject and group settlement
    fun rejectOrder(order: OrderRecord) {
        viewModelScope.launch {
            repository.updateOrderStatus(order.id, order, OrderStatus.REJECTED)
        }
    }

    fun payAllOrdersForTable(tableOrders: List<OrderRecord>) {
        viewModelScope.launch {
            repository.payOrders(tableOrders)
        }
    }

    fun advanceTableSession(orders: List<OrderRecord>, newStatus: OrderStatus) {
        viewModelScope.launch {
            val tableNumber = orders.firstOrNull()?.tableNumber ?: 0
            if (newStatus == OrderStatus.DELIVERED) {
                repository.autoMergeCustomerOrdersForTable(tableNumber, orders)
            } else {
                orders.forEach { order ->
                    if (order.status != OrderStatus.PAID) {
                        repository.updateOrderStatus(order.id, order, newStatus)
                    }
                }
            }
        }
    }

    fun mergeCustomerOrders(ordersToMerge: List<OrderRecord>) {
        viewModelScope.launch {
            repository.mergeOrdersIntoOne(ordersToMerge)
        }
    }

    fun mergeNewOrderWithPreparing(newOrder: OrderRecord, preparingOrders: List<OrderRecord>) {
        viewModelScope.launch {
            repository.mergeNewOrderIntoPreparing(newOrder, preparingOrders)
        }
    }

    fun rejectTableSession(orders: List<OrderRecord>) {
        viewModelScope.launch {
            orders.forEach { order ->
                if (order.status != OrderStatus.PAID) {
                    repository.updateOrderStatus(order.id, order, OrderStatus.REJECTED)
                }
            }
        }
    }

    fun payOrders(orders: List<OrderRecord>) {
        viewModelScope.launch {
            repository.payOrders(orders)
        }
    }

    fun deleteTableSession(orders: List<OrderRecord>) {
        viewModelScope.launch {
            orders.forEach { order ->
                if (order.status != OrderStatus.PAID) {
                    repository.deleteOrder(order)
                }
            }
        }
    }

    fun deleteAuthorizedPaidOrder(
        order: OrderRecord,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.deleteAuthorizedPaidOrder(order, code)
            if (success) {
                onSuccess()
            } else {
                onError("كود الموافقة غير صحيح! يرجى التواصل مع الإدارة عبر الواتساب للحصول على الكود الصحيح.")
            }
        }
    }

    fun deleteAllAuthorizedPaidOrders(
        code: String,
        onSuccess: (deletedCount: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteAllAuthorizedPaidOrders(code)
            result.fold(
                onSuccess = { count -> onSuccess(count) },
                onFailure = { err -> onError(err.message ?: "تعذر تصفير الأرشيف، يرجى التأكد من كود الموافقة.") }
            )
        }
    }

    // Auth actions
    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authError.value = "يرجى إدخال البريد الإلكتروني وكلمة المرور"
            return
        }
        _authLoading.value = true
        _authError.value = null
        _authSuccessMessage.value = null
        _unverifiedEmail.value = null

        viewModelScope.launch {
            try {
                when (val result = repository.login(email, pass)) {
                    is AuthResult.Success -> {
                        val user = result.user
                        repository.currentCafeteriaId = user.id

                        val cachedProfile = repository.loadCafeProfile(user.id)
                        val effectiveCafeName = cachedProfile["cafeName"] ?: user.cafeName
                        val effectiveOwnerName = cachedProfile["ownerName"] ?: user.fullName
                        val effectiveEmail = cachedProfile["email"] ?: user.email

                        _currentUser.value = user.copy(
                            cafeName = effectiveCafeName,
                            fullName = effectiveOwnerName,
                            email = effectiveEmail
                        )
                        _cafeName.value = effectiveCafeName
                        _cafeOwnerName.value = effectiveOwnerName
                        _cafeEmail.value = effectiveEmail
                        cachedProfile["phone"]?.let { _cafePhone.value = it }
                        cachedProfile["address"]?.let { _cafeAddress.value = it }
                        cachedProfile["welcomeNote"]?.let { _cafeWelcomeNote.value = it }
                        cachedProfile["logoType"]?.let { _cafeLogoType.value = it }
                        cachedProfile["customLogoUri"]?.let { _cafeCustomLogoUri.value = it.ifBlank { null } }
                        cachedProfile["menuUrl"]?.let {
                            val resolved = resolveMenuUrl(it)
                            _webMenuUrl.value = resolved
                            repository.baseWebMenuUrl = resolved
                        }

                        repository.isSystemShutdown.value = repository.loadSystemShutdownPref(user.id)
                        repository.updateExistingTablesCafeteriaId()
                        repository.startFirestoreOrdersListener(viewModelScope, user.id)
                        repository.startCafeteriaStatusListener(user.id)
                        repository.loadMenuForCafeteria(user.id)

                        viewModelScope.launch(Dispatchers.IO) {
                            repository.fetchAndSyncCafeProfileFromFirestore(user.id)?.let { remote ->
                                remote["cafeName"]?.let { if (it.isNotBlank()) _cafeName.value = it }
                                remote["phone"]?.let { if (it.isNotBlank()) _cafePhone.value = it }
                                remote["ownerName"]?.let { if (it.isNotBlank()) _cafeOwnerName.value = it }
                                remote["email"]?.let { if (it.isNotBlank()) _cafeEmail.value = it }
                                remote["address"]?.let { if (it.isNotBlank()) _cafeAddress.value = it }
                                remote["welcomeNote"]?.let { if (it.isNotBlank()) _cafeWelcomeNote.value = it }
                                remote["logoType"]?.let { if (it.isNotBlank()) _cafeLogoType.value = it }
                                remote["customLogoUri"]?.let { _cafeCustomLogoUri.value = it.ifBlank { null } }
                                remote["menuUrl"]?.let {
                                    val resolved = resolveMenuUrl(it)
                                    _webMenuUrl.value = resolved
                                    repository.baseWebMenuUrl = resolved
                                }
                            }
                        }

                        _authError.value = null
                        _currentScreen.value = AppScreen.CASHIER_ORDERS
                    }
                    is AuthResult.EmailNotVerified -> {
                        _unverifiedEmail.value = result.email
                        _authError.value = result.message
                    }
                    is AuthResult.Error -> {
                        _authError.value = result.message
                    }
                    else -> {}
                }
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun register(fullName: String, email: String, pass: String, confirmPass: String, cafeName: String) {
        if (fullName.isBlank() || email.isBlank() || pass.isBlank()) {
            _authError.value = "يرجى ملء جميع الحقول المطلوبة"
            return
        }
        if (pass.length < 6) {
            _authError.value = "كلمة المرور يجب أن لا تقل عن 6 أحرف"
            return
        }
        if (pass != confirmPass) {
            _authError.value = "كلمتا المرور غير متطابقتين"
            return
        }

        _authLoading.value = true
        _authError.value = null
        _authSuccessMessage.value = null
        _registrationSuccessMessage.value = null

        viewModelScope.launch {
            try {
                when (val result = repository.register(fullName, email, pass, cafeName)) {
                    is AuthResult.VerificationEmailSent -> {
                        _registrationSuccessMessage.value = result.message
                        _unverifiedEmail.value = result.email
                    }
                    is AuthResult.Success -> {
                        _registrationSuccessMessage.value = "تم إنشاء الحساب بنجاح! يرجى تسجيل الدخول بعد التحقق من بريدك الإلكتروني."
                        _unverifiedEmail.value = result.user.email
                    }
                    is AuthResult.Error -> {
                        _authError.value = result.message
                    }
                    else -> {}
                }
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun resendVerificationEmail(email: String, password: String? = null) {
        if (email.isBlank()) {
            _authError.value = "يرجى إدخال البريد الإلكتروني"
            return
        }
        _authLoading.value = true
        _authError.value = null
        _authSuccessMessage.value = null

        viewModelScope.launch {
            try {
                when (val result = repository.resendEmailVerification(email, password)) {
                    is AuthResult.VerificationEmailSent -> {
                        _authSuccessMessage.value = result.message
                    }
                    is AuthResult.Error -> {
                        _authError.value = result.message
                    }
                    else -> {}
                }
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank() || !email.contains("@")) {
            _authError.value = "يرجى إدخال بريد إلكتروني صحيح"
            return
        }
        _authLoading.value = true
        _authError.value = null
        _authSuccessMessage.value = null

        viewModelScope.launch {
            try {
                when (val result = repository.sendPasswordReset(email)) {
                    is AuthResult.VerificationEmailSent -> {
                        _authSuccessMessage.value = result.message
                    }
                    is AuthResult.Error -> {
                        _authError.value = result.message
                    }
                    else -> {}
                }
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun dismissRegistrationSuccess() {
        _registrationSuccessMessage.value = null
        _currentScreen.value = AppScreen.LOGIN
    }

    fun logout() {
        repository.logout()
        repository.stopCafeteriaStatusListener()
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLocalMenuAndOrders()
        }
        _currentUser.value = null
        _cart.value = emptyList()
        _currentScreen.value = AppScreen.LOGIN
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopAppUpdateListener()
    }
}
