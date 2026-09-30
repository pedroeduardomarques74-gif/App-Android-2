from pathlib import Path
p=Path("app/src/main/java/com/bigger/corridas/MainActivity.kt")
s=p.read_text()

old='''        val title = TextView(this).apply {
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
        }'''

new='''        val title = TextView(this).apply {
            text = "${place.categoryIcon}  ${place.displayName}"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }
        val secondary = TextView(this).apply {
            val where = place.secondary.ifBlank { place.formatted }
            val distance = place.distanceKm?.let { " • ${Format.n(it, 1)} km" }.orEmpty()
            text = "${place.category.uppercase()} • $where$distance"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            textSize = 13f
            setPadding(dp(28), dp(4), 0, 0)
            maxLines = 2
        }'''

if old not in s:
    raise SystemExit("addSuggestionRow block not found")
s=s.replace(old,new)

old2='''                if (places.isEmpty()) {
                    b.txtSearchTitle.text = "Nenhum endereço encontrado. Tente rua, número, bairro ou cidade."
                } else {'''
new2='''                if (places.isEmpty()) {
                    b.txtSearchTitle.text = if (q.matches(Regex("^\\\\d{1,6}[A-Za-z]?$")) && !broadSearch)
                        "Não encontrei o número $q nos endereços mapeados próximos. Digite também o nome da rua."
                    else
                        "Nenhum endereço encontrado. Tente rua, número, bairro, estabelecimento ou cidade."
                } else {'''
if old2 not in s:
    raise SystemExit("empty message block not found")
s=s.replace(old2,new2)

old3='''        return when {
            !city.isNullOrBlank() && !state.isNullOrBlank() -> "Primeiro, resultados em $city • $state"
            !city.isNullOrBlank() -> "Primeiro, resultados em $city"
            else -> "Resultados próximos da origem"
        }'''
new3='''        if (lastSearchText.matches(Regex("^\\\\d{1,6}[A-Za-z]?$")) && !broadSearch) {
            return "Número $lastSearchText • endereços próximos"
        }
        return when {
            !city.isNullOrBlank() && !state.isNullOrBlank() -> "Primeiro, resultados em $city • $state"
            !city.isNullOrBlank() -> "Primeiro, resultados em $city"
            else -> "Resultados próximos da origem"
        }'''
if old3 not in s:
    raise SystemExit("localSearchTitle block not found")
s=s.replace(old3,new3)

p.write_text(s)
print("patched MainActivity v2.1")
