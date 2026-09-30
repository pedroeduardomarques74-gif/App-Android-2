package com.bigger.corridas

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.bigger.corridas.data.*
import com.bigger.corridas.databinding.ActivityMainBinding
import com.bigger.corridas.domain.*
import com.bigger.corridas.location.*
import com.bigger.corridas.navigation.NavigationHelper
import com.bigger.corridas.route.*
import com.bigger.corridas.ui.*
import com.bigger.corridas.util.Format
import com.bigger.corridas.util.UiInsets
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var store: SettingsStore
    private lateinit var profileStore: ProfileStore
    private val db by lazy { AppDatabase.get(this) }
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val addressSearch by lazy { AddressSearchService() }
    private val geocoder by lazy { GeocodingRepository() }
    private val recents by lazy { RecentAddressRepository(this) }
    private val favorites by lazy { FavoritesRepository(this) }
    private val searchController by lazy { AddressSearchController(lifecycleScope) }

    private var currentResult: FareResult? = null
    private var clientPrice: Double? = null
    private var lastOrigin = ""
    private var lastDestination = ""
    private var routeKm = 0.0
    private var routeMin = 0.0
    private var waitingStartedAt: Long? = null
    private var waitingMinutes = 0.0

    private var originPlace: AddressPlace? = null
    private var destinationPlace: AddressPlace? = null
    private var searchingForOrigin = false
    private var broadSearch = false
    private var suppressTextEvents = false
    private var lastSearchText = ""

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true) useLocation()
        else toast("Sem acesso à localização. Você pode pesquisar a origem manualmente.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        UiInsets.apply(this, b.root)
        store = SettingsStore(this)
        profileStore = ProfileStore(this)
        refreshProfiles()
        setupAddressExperience()
        setupActions()
        showQuickAddresses()
        if (hasLocationPermission()) useLocation() else b.txtOriginSecondary.text = "Toque em “Localização atual” ou pesquise um endereço"
    }

    private fun setupActions() {
        b.btnLocation.setOnClickListener { requestLocation() }
        b.btnSwap.setOnClickListener { swapOriginDestination() }
        b.btnClearOrigin.setOnClickListener { clearOrigin() }
        b.btnClearDestination.setOnClickListener { clearDestination() }
        b.btnOtherCities.setOnClickListener { broadSearch = !broadSearch; runSearch(lastSearchText) }
        b.btnFavorites.setOnClickListener { showFavoritesDialog() }
        b.btnWaze.setOnClickListener { destinationPlace?.let { NavigationHelper.openWaze(this, it) } ?: toast("Selecione um destino válido.") }
        b.btnNavigate.setOnClickListener { destinationPlace?.let { NavigationHelper.navigate(this, it) } ?: toast("Selecione um destino válido.") }
        b.btnDestFavorite.setOnClickListener { destinationPlace?.let { promptFavoriteName(it) } ?: toast("Selecione um destino válido.") }
        b.btnDestEdit.setOnClickListener { b.inputDestination.requestFocus(); showKeyboard(b.inputDestination) }
        b.btnCalculate.setOnClickListener { calculateRoute(false) }
        b.btnBreakdown.setOnClickListener { showBreakdown() }
        b.btnAdjust.setOnClickListener { adjustPrice() }
        b.btnShare.setOnClickListener { shareQuote() }
        b.btnWait.setOnClickListener { toggleWaiting() }
        b.btnSave.setOnClickListener { saveTrip() }
        b.navSimulator.setOnClickListener { startActivity(Intent(this, SimulatorActivity::class.java)) }
        b.navHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        b.navSummary.setOnClickListener { startActivity(Intent(this, SummaryActivity::class.java)) }
        b.navSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun setupAddressExperience() {
        b.inputOrigin.setOnFocusChangeListener { _, focused -> if (focused) { searchingForOrigin = true; broadSearch = false; showQuickAddresses() } }
        b.inputDestination.setOnFocusChangeListener { _, focused -> if (focused) { searchingForOrigin = false; broadSearch = false; showQuickAddresses() } }

        b.inputOrigin.doAfterTextChanged { editable ->
            if (suppressTextEvents) return@doAfterTextChanged
            originPlace = null
            b.txtOriginSecondary.text = "Origem ainda não confirmada"
            handleSearchText(editable?.toString().orEmpty(), true)
        }
        b.inputDestination.doAfterTextChanged { editable ->
            if (suppressTextEvents) return@doAfterTextChanged
            destinationPlace = null
            hideSelectedDestination()
            handleSearchText(editable?.toString().orEmpty(), false)
        }
    }

    private fun handleSearchText(raw: String, origin: Boolean) {
        searchingForOrigin = origin
        broadSearch = false
        val q = raw.trim()
        lastSearchText = q
        if (q.length < 2) { searchController.cancel(); showQuickAddresses(); return }
        searchController.submit(q) { runSearch(q) }
    }

    private suspend fun performSearch(q: String): List<AddressPlace> = withContext(Dispatchers.IO) {
        val context = AddressSearchContext(
            origin = originPlace?.point ?: destinationPlace?.point,
            city = originPlace?.city,
            state = originPlace?.state,
            broad = broadSearch
        )
        addressSearch.search(q, context, 8).getOrThrow()
    }

    private fun runSearch(q: String) {
        if (q.length < 2) return
        lastSearchText = q
        lifecycleScope.launch {
            b.addressPanel.visibility = View.VISIBLE
            b.progressAddress.visibility = View.VISIBLE
            b.txtSearchTitle.text = if (broadSearch) "Buscando em outras cidades…" else localSearchTitle()
            b.suggestionContainer.removeAllViews()
            val result = runCatching { performSearch(q) }
            b.progressAddress.visibility = View.GONE
            result.onSuccess { places ->
                if (q != lastSearchText) return@onSuccess
                if (places.isEmpty()) {
                    b.txtSearchTitle.text = "Nenhum endereço encontrado. Tente rua, número, bairro ou cidade."
                } else {
                    b.txtSearchTitle.text = if (broadSearch) "Resultados no Brasil" else localSearchTitle()
                    places.forEach { addSuggestionRow(it) }
                }
                b.btnOtherCities.text = if (broadSearch) "← Priorizar cidade atual" else "🌎 Buscar em outras cidades"
                b.btnOtherCities.visibility = View.VISIBLE
            }.onFailure {
                b.txtSearchTitle.text = "Não foi possível pesquisar endereços. Verifique sua conexão."
                b.btnOtherCities.visibility = View.VISIBLE
            }
        }
    }

    private fun localSearchTitle(): String {
        val city = originPlace?.city
        val state = originPlace?.state
        return when {
            !city.isNullOrBlank() && !state.isNullOrBlank() -> "Primeiro, resultados em $city • $state"
            !city.isNullOrBlank() -> "Primeiro, resultados em $city"
            else -> "Resultados próximos da origem"
        }
    }

    private fun addSuggestionRow(place: AddressPlace) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.address_row_bg)
            setOnClickListener { selectAddress(place, searchingForOrigin) }
        }
        val title = TextView(this).apply {
            text = "📍  ${place.displayName}"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }
        val secondary = TextView(this).apply {
            text = place.secondary.ifBlank { place.formatted }
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            textSize = 13f
            setPadding(dp(28), dp(4), 0, 0)
            maxLines = 2
        }
        row.addView(title)
        row.addView(secondary)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) }
        b.suggestionContainer.addView(row, lp)
    }

    private fun selectAddress(place: AddressPlace, asOrigin: Boolean) {
        if (!place.street.isNullOrBlank() && place.number.isNullOrBlank() && !looksLikePoi(place)) {
            requestHouseNumber(place, asOrigin)
        } else completeAddressSelection(place, asOrigin)
    }

    private fun looksLikePoi(place: AddressPlace): Boolean =
        place.street.isNullOrBlank() || (place.displayName != place.street && place.displayName.isNotBlank())

    private fun requestHouseNumber(place: AddressPlace, asOrigin: Boolean) {
        val input = EditText(this).apply {
            hint = "Ex.: 1600"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle(place.street ?: place.displayName)
            .setMessage("Qual é o número? Isso melhora a precisão da rota.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Sem número / ponto conhecido") { _, _ -> completeAddressSelection(place, asOrigin) }
            .setPositiveButton("Confirmar") { _, _ ->
                val number = input.text.toString().trim()
                if (number.isBlank()) { toast("Informe o número ou escolha “Sem número”."); return@setPositiveButton }
                lifecycleScope.launch {
                    b.txtSearchTitle.text = "Confirmando número…"
                    b.progressAddress.visibility = View.VISIBLE
                    val query = listOfNotNull(place.street, number, place.neighborhood, place.city, place.state, "Brasil").joinToString(", ")
                    val ctx = AddressSearchContext(originPlace?.point, place.city ?: originPlace?.city, place.state ?: originPlace?.state, false)
                    val found = withContext(Dispatchers.IO) { addressSearch.search(query, ctx, 5).getOrDefault(emptyList()) }
                    b.progressAddress.visibility = View.GONE
                    val exact = found.firstOrNull { it.number?.equals(number, true) == true } ?: found.firstOrNull()
                    if (exact != null) completeAddressSelection(exact, asOrigin)
                    else toast("Não consegui confirmar esse número. Tente pesquisar rua + número.")
                }
            }.show()
    }

    private fun completeAddressSelection(place: AddressPlace, asOrigin: Boolean) {
        suppressTextEvents = true
        if (asOrigin) {
            originPlace = place
            b.inputOrigin.setText(place.displayName)
            b.txtOriginSecondary.text = place.secondary.ifBlank { place.formatted }
            b.btnClearOrigin.visibility = View.VISIBLE
        } else {
            destinationPlace = place
            b.inputDestination.setText(place.displayName)
            b.txtDestinationSecondary.text = place.secondary.ifBlank { place.formatted }
            b.btnClearDestination.visibility = View.VISIBLE
            showSelectedDestination(place)
            recents.add(place)
        }
        suppressTextEvents = false
        searchController.cancel()
        b.addressPanel.visibility = View.GONE
        hideKeyboard()
        if (!asOrigin && originPlace != null) calculateRoute(true)
    }

    private fun showSelectedDestination(place: AddressPlace) {
        b.selectedDestinationCard.visibility = View.VISIBLE
        b.txtSelectedDestination.text = place.displayName
        b.txtSelectedDestinationSecondary.text = place.secondary.ifBlank { place.formatted }
    }

    private fun hideSelectedDestination() { b.selectedDestinationCard.visibility = View.GONE }

    private fun showQuickAddresses() {
        b.addressPanel.visibility = View.VISIBLE
        b.progressAddress.visibility = View.GONE
        b.suggestionContainer.removeAllViews()
        val favs = favorites.list().take(4)
        val recent = recents.list().take(5)
        if (favs.isEmpty() && recent.isEmpty()) {
            b.txtSearchTitle.text = if (searchingForOrigin) "Pesquise sua origem" else "Digite rua, número, bairro ou local"
            b.btnOtherCities.visibility = View.GONE
            return
        }
        b.txtSearchTitle.text = if (favs.isNotEmpty()) "Favoritos e recentes" else "Endereços recentes"
        favs.forEach { fav -> addQuickRow("★ ${fav.name}", fav.place) }
        recent.filterNot { r -> favs.any { it.place.formatted == r.formatted } }.forEach { addQuickRow("↻ ${it.displayName}", it) }
        b.btnOtherCities.visibility = View.GONE
    }

    private fun addQuickRow(title: String, place: AddressPlace) {
        val btn = MaterialButton(this).apply {
            text = "$title\n${place.secondary.ifBlank { place.formatted }}"
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textSize = 13f
            setPadding(dp(12), dp(5), dp(12), dp(5))
            minHeight = dp(56)
            setOnClickListener { completeAddressSelection(place, searchingForOrigin) }
        }
        b.suggestionContainer.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun requestLocation() {
        if (hasLocationPermission()) useLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun useLocation() {
        if (!hasLocationPermission()) return
        b.txtOriginSecondary.text = "Obtendo localização atual…"
        try {
            locationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc == null) { toast("Não consegui obter sua posição. Ative o GPS ou pesquise a origem."); return@addOnSuccessListener }
                lifecycleScope.launch {
                    val point = Point(loc.latitude, loc.longitude)
                    val place = withContext(Dispatchers.IO) { geocoder.reverse(point).getOrNull() }
                    if (place == null) {
                        toast("Localização obtida, mas não foi possível identificar o endereço. Pesquise a origem manualmente.")
                        return@launch
                    }
                    originPlace = place
                    suppressTextEvents = true
                    b.inputOrigin.setText("Minha localização atual")
                    suppressTextEvents = false
                    b.txtOriginSecondary.text = place.routeLabel()
                    b.btnClearOrigin.visibility = View.VISIBLE
                    showQuickAddresses()
                }
            }.addOnFailureListener { toast("Falha ao obter localização. Digite a origem manualmente.") }
        } catch (_: SecurityException) { toast("Permissão de localização necessária.") }
    }

    private fun swapOriginDestination() {
        val oldOrigin = originPlace
        val oldDest = destinationPlace
        if (oldOrigin == null && oldDest == null) return
        originPlace = oldDest
        destinationPlace = oldOrigin
        suppressTextEvents = true
        if (originPlace != null) {
            b.inputOrigin.setText(originPlace!!.displayName)
            b.txtOriginSecondary.text = originPlace!!.secondary.ifBlank { originPlace!!.formatted }
        } else { b.inputOrigin.setText(""); b.txtOriginSecondary.text = "Escolha a origem" }
        if (destinationPlace != null) {
            b.inputDestination.setText(destinationPlace!!.displayName)
            b.txtDestinationSecondary.text = destinationPlace!!.secondary.ifBlank { destinationPlace!!.formatted }
            showSelectedDestination(destinationPlace!!)
        } else { b.inputDestination.setText(""); b.txtDestinationSecondary.text = "Rua, número, bairro ou local"; hideSelectedDestination() }
        suppressTextEvents = false
        if (originPlace != null && destinationPlace != null) calculateRoute(true)
    }

    private fun clearOrigin() {
        originPlace = null
        suppressTextEvents = true; b.inputOrigin.setText(""); suppressTextEvents = false
        b.txtOriginSecondary.text = "Toque em “Localização atual” ou pesquise"
        b.btnClearOrigin.visibility = View.GONE
    }

    private fun clearDestination() {
        destinationPlace = null
        suppressTextEvents = true; b.inputDestination.setText(""); suppressTextEvents = false
        b.txtDestinationSecondary.text = "Rua, número, bairro ou local"
        b.btnClearDestination.visibility = View.GONE
        hideSelectedDestination(); showQuickAddresses()
    }

    private fun calculateRoute(auto: Boolean) {
        val origin = originPlace
        val destination = destinationPlace
        if (origin == null || destination == null) {
            if (!auto) toast("Selecione uma origem e um destino válidos nas sugestões.")
            return
        }
        b.btnCalculate.isEnabled = false
        b.btnCalculate.text = "Calculando rota…"
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { RouteService().route(origin.point, destination.point) }
            b.btnCalculate.isEnabled = true
            b.btnCalculate.text = "Recalcular rota"
            outcome.onSuccess { routes ->
                val r = routes.first()
                lastOrigin = origin.routeLabel(); lastDestination = destination.routeLabel()
                routeKm = r.distanceKm; routeMin = r.durationMin
                currentResult = calculateCurrentFare(); clientPrice = currentResult!!.recommended
                render(currentResult!!, routes.size)
                b.routePreview.visibility = View.VISIBLE
                b.txtRoutePreview.text = "📍 ${origin.displayName}\n   ↓\n🏁 ${destination.displayName}\n${Format.n(r.distanceKm)} km • ${Format.n(r.durationMin,0)} min"
            }.onFailure { toast(it.message ?: "Não foi possível calcular a rota. Verifique a internet.") }
        }
    }

    private fun showFavoritesDialog() {
        val favs = favorites.list()
        if (favs.isEmpty()) { toast("Você ainda não salvou favoritos."); return }
        AlertDialog.Builder(this).setTitle("Favoritos").setItems(favs.map { "${it.name} — ${it.place.displayName}" }.toTypedArray()) { _, which ->
            completeAddressSelection(favs[which].place, searchingForOrigin)
        }.show()
    }

    private fun promptFavoriteName(place: AddressPlace) {
        val input = EditText(this).apply { hint = "Casa, Trabalho, Cliente..." }
        AlertDialog.Builder(this).setTitle("Salvar favorito").setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
            val name = input.text.toString().trim().ifBlank { "Favorito" }
            favorites.add(name, place); toast("Favorito salvo.")
        }.show()
    }

    private fun activeTariff(): TariffConfig = profileStore.load(b.profileSpinner.selectedItem?.toString() ?: "Padrão")
    private fun refreshProfiles(){ b.profileSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, profileStore.names()).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) } }
    override fun onResume(){ super.onResume(); if(::profileStore.isInitialized) refreshProfiles() }
    private fun calculateCurrentFare(): FareResult = FareCalculator.calculate(TripInput(routeKm,routeMin,waitingMinutes=waitingMinutes,roundTrip=b.checkRoundTrip.isChecked,emptyReturn=b.checkEmptyReturn.isChecked,applyNight=b.checkNight.isChecked,applyPeak=b.checkPeak.isChecked,applyRain=b.checkRain.isChecked,applyRoad=b.checkRoad.isChecked), activeTariff(), store.vehicle())

    private fun toggleWaiting() {
        if(currentResult==null) return toast("Calcule uma corrida primeiro.")
        val start=waitingStartedAt
        if(start==null){ waitingStartedAt=System.currentTimeMillis(); b.btnWait.text="PARAR ESPERA"; toast("Cronômetro de espera iniciado.") }
        else { val elapsed=(System.currentTimeMillis()-start)/60000.0; waitingMinutes += elapsed.coerceAtLeast(0.0); waitingStartedAt=null; b.btnWait.text="INICIAR ESPERA"; currentResult=calculateCurrentFare(); clientPrice=currentResult!!.recommended; render(currentResult!!); toast("Espera adicionada: ${Format.n(elapsed,1)} min") }
    }

    private fun render(r: FareResult, alternatives: Int = 1) {
        val price = clientPrice ?: r.recommended
        b.txtRecommended.text = Format.money(price)
        b.txtRoute.text = if (alternatives > 1) "${Format.n(r.totalKm)} km • ${Format.n(r.totalMinutes,0)} min • $alternatives rotas" else "${Format.n(r.totalKm)} km • ${Format.n(r.totalMinutes,0)} min"
        b.txtMetrics.text = "Distância: ${Format.n(r.totalKm)} km\nTempo: ${Format.n(r.totalMinutes,0)} min\nCusto estimado: ${Format.money(r.estimatedCost)}\nLucro estimado: ${Format.money(price-r.estimatedCost)}\nR$/km: ${Format.money(if(r.totalKm>0) price/r.totalKm else 0.0)}/km\nR$/hora: ${Format.money(if(r.totalMinutes>0) price/(r.totalMinutes/60.0) else 0.0)}/h"
        b.txtSuggested.text = "Econômico: ${Format.money(r.economy)}   Recomendado: ${Format.money(r.recommended)}   Premium: ${Format.money(r.premium)}"
    }

    private fun showBreakdown() {
        val r = currentResult ?: return toast("Calcule uma corrida primeiro.")
        val text = r.breakdown.joinToString("\n") { "${it.label}: ${Format.money(it.amount)}" } + "\n\nPreço recomendado: ${Format.money(r.recommended)}"
        AlertDialog.Builder(this).setTitle("Detalhamento do cálculo").setMessage(text).setPositiveButton("OK",null).show()
    }

    private fun adjustPrice() {
        val r = currentResult ?: return toast("Calcule uma corrida primeiro.")
        val ops=arrayOf("- R$ 5","- R$ 2","- R$ 1","+ R$ 1","+ R$ 2","+ R$ 5","Digitar valor")
        AlertDialog.Builder(this).setTitle("Ajustar valor para o cliente").setItems(ops){_,which ->
            val delta=listOf(-5.0,-2.0,-1.0,1.0,2.0,5.0).getOrNull(which)
            if(delta!=null){ clientPrice=maxOf(0.0,(clientPrice?:r.recommended)+delta); render(r) }
            else { val input=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText((clientPrice?:r.recommended).toString()) }; AlertDialog.Builder(this).setTitle("Digite o valor").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Aplicar"){_,_->clientPrice=Format.parse(input.text.toString(),r.recommended).coerceAtLeast(0.0);render(r)}.show() }
        }.show()
    }

    private fun shareQuote() {
        val r = currentResult ?: return toast("Calcule uma corrida primeiro.")
        val price = clientPrice ?: r.recommended
        val text = "🚘 ORÇAMENTO DA CORRIDA\n\n📍 Origem:\n$lastOrigin\n\n🏁 Destino:\n$lastDestination\n\n🛣 Distância:\n${Format.n(r.totalKm)} km\n\n⏱ Tempo estimado:\n${Format.n(r.totalMinutes,0)} min\n\n💰 Valor da viagem:\n${Format.money(price)}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text) },"Compartilhar orçamento"))
    }

    private fun saveTrip() {
        val r = currentResult ?: return toast("Calcule uma corrida primeiro.")
        lifecycleScope.launch(Dispatchers.IO) {
            db.dao().insertTrip(TripHistoryEntity(timestamp=System.currentTimeMillis(), origin=lastOrigin, destination=lastDestination, distanceKm=r.totalKm,durationMin=r.totalMinutes,calculatedPrice=r.recommended,chargedPrice=clientPrice?:r.recommended,costs=r.estimatedCost,profit=(clientPrice?:r.recommended)-r.estimatedCost))
            withContext(Dispatchers.Main) { toast("Corrida salva no histórico.") }
        }
    }

    private fun showKeyboard(v: View) { (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) }
    private fun hideKeyboard() { currentFocus?.let { (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(it.windowToken, 0); it.clearFocus() } }
    private fun dp(v:Int)= (v * resources.displayMetrics.density).toInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
