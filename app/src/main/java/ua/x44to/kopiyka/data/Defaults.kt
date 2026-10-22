package ua.x44to.kopiyka.data

/** Стартовий набір: два рахунки й категорії, з якими можна одразу записувати витрати. */
object Defaults {
    private class C(val id: String, val uk: String, val en: String, val icon: String, val color: Long)

    private val expense = listOf(
        C("food", "Продукти", "Groceries", "cart", 0xFF43A047),
        C("cafe", "Кафе й ресторани", "Cafes & restaurants", "restaurant", 0xFFFB8C00),
        C("transport", "Транспорт", "Transport", "bus", 0xFF1E88E5),
        C("car", "Авто й пальне", "Car & fuel", "fuel", 0xFF546E7A),
        C("home", "Житло й комуналка", "Home & utilities", "home", 0xFF8E24AA),
        C("phone", "Зв'язок та інтернет", "Phone & internet", "wifi", 0xFF00ACC1),
        C("health", "Здоров'я", "Health", "hospital", 0xFFE53935),
        C("clothes", "Одяг і взуття", "Clothes", "clothes", 0xFFD81B60),
        C("fun", "Розваги", "Entertainment", "movie", 0xFF5E35B1),
        C("subs", "Підписки", "Subscriptions", "subscriptions", 0xFF3949AB),
        C("gifts", "Подарунки", "Gifts", "gift", 0xFFF4511E),
        C("edu", "Освіта", "Education", "school", 0xFF039BE5),
        C("sport", "Спорт", "Sport", "fitness", 0xFF7CB342),
        C("beauty", "Краса й догляд", "Beauty & care", "spa", 0xFFEC407A),
        C("pets", "Тварини", "Pets", "pets", 0xFF6D4C41),
        C("kids", "Діти", "Kids", "child", 0xFFFFB300),
        C("travel", "Подорожі", "Travel", "flight", 0xFF00897B),
        C("donate", "Донати", "Donations", "volunteer", 0xFFFDD835),
        C("other", "Інше", "Other", "more", 0xFF757575),
    )

    private val income = listOf(
        C("salary", "Зарплата", "Salary", "work", 0xFF2E7D6B),
        C("side", "Підробіток", "Side job", "laptop", 0xFF1E88E5),
        C("gift_in", "Подарунки", "Gifts", "gift", 0xFFF4511E),
        C("cashback", "Кешбек і відсотки", "Cashback & interest", "percent", 0xFF7CB342),
        C("sale", "Продаж речей", "Sales", "sell", 0xFFFB8C00),
        C("other_in", "Інше", "Other", "more", 0xFF757575),
    )

    fun categories(): List<Category> =
        expense.map { Category(it.id, t(it.uk, it.en), TxType.EXPENSE, it.icon, it.color) } +
            income.map { Category(it.id, t(it.uk, it.en), TxType.INCOME, it.icon, it.color) }

    fun accounts(): List<Account> {
        val cur = if (L.ukr) "UAH" else "USD"
        return listOf(
            Account("card", t("Картка", "Card"), AccType.CARD, cur, 0, 0xFF1E88E5),
            Account("cash", t("Готівка", "Cash"), AccType.CASH, cur, 0, 0xFF43A047),
        )
    }

    fun data(): AppData {
        val base = if (L.ukr) "UAH" else "USD"
        return AppData(accounts = accounts(), categories = categories(), settings = Settings(base = base))
    }
}
