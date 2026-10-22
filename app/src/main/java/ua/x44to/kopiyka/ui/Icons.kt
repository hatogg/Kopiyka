package ua.x44to.kopiyka.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import ua.x44to.kopiyka.data.AccType

/** Іконки категорій і цілей: ключ зберігається в даних, картинка — тут. */
val CAT_ICONS: List<Pair<String, ImageVector>> = listOf(
    "cart" to Icons.Rounded.ShoppingCart,
    "bag" to Icons.Rounded.ShoppingBag,
    "restaurant" to Icons.Rounded.Restaurant,
    "cafe" to Icons.Rounded.LocalCafe,
    "fastfood" to Icons.Rounded.Fastfood,
    "bar" to Icons.Rounded.LocalBar,
    "cake" to Icons.Rounded.Cake,
    "bus" to Icons.Rounded.DirectionsBus,
    "train" to Icons.Rounded.Train,
    "taxi" to Icons.Rounded.LocalTaxi,
    "car" to Icons.Rounded.DirectionsCar,
    "fuel" to Icons.Rounded.LocalGasStation,
    "parking" to Icons.Rounded.LocalParking,
    "bike" to Icons.Rounded.DirectionsBike,
    "flight" to Icons.Rounded.Flight,
    "hotel" to Icons.Rounded.Hotel,
    "beach" to Icons.Rounded.BeachAccess,
    "home" to Icons.Rounded.Home,
    "chair" to Icons.Rounded.Chair,
    "bolt" to Icons.Rounded.Bolt,
    "water" to Icons.Rounded.WaterDrop,
    "fire" to Icons.Rounded.LocalFireDepartment,
    "laundry" to Icons.Rounded.LocalLaundryService,
    "cleaning" to Icons.Rounded.CleaningServices,
    "repair" to Icons.Rounded.Handyman,
    "build" to Icons.Rounded.Build,
    "wifi" to Icons.Rounded.Wifi,
    "phone" to Icons.Rounded.PhoneAndroid,
    "computer" to Icons.Rounded.Computer,
    "subscriptions" to Icons.Rounded.Subscriptions,
    "hospital" to Icons.Rounded.LocalHospital,
    "pharmacy" to Icons.Rounded.LocalPharmacy,
    "pill" to Icons.Rounded.Medication,
    "clothes" to Icons.Rounded.Checkroom,
    "spa" to Icons.Rounded.Spa,
    "haircut" to Icons.Rounded.ContentCut,
    "fitness" to Icons.Rounded.FitnessCenter,
    "movie" to Icons.Rounded.Movie,
    "games" to Icons.Rounded.SportsEsports,
    "music" to Icons.Rounded.MusicNote,
    "park" to Icons.Rounded.Park,
    "party" to Icons.Rounded.Celebration,
    "gift" to Icons.Rounded.CardGiftcard,
    "redeem" to Icons.Rounded.Redeem,
    "school" to Icons.Rounded.School,
    "book" to Icons.Rounded.Book,
    "child" to Icons.Rounded.ChildCare,
    "pets" to Icons.Rounded.Pets,
    "volunteer" to Icons.Rounded.VolunteerActivism,
    "store" to Icons.Rounded.Store,
    "receipt" to Icons.Rounded.Receipt,
    "work" to Icons.Rounded.Work,
    "laptop" to Icons.Rounded.Laptop,
    "percent" to Icons.Rounded.Percent,
    "sell" to Icons.Rounded.Sell,
    "paid" to Icons.Rounded.Paid,
    "money" to Icons.Rounded.AttachMoney,
    "payments" to Icons.Rounded.Payments,
    "exchange" to Icons.Rounded.CurrencyExchange,
    "bank" to Icons.Rounded.AccountBalance,
    "card" to Icons.Rounded.CreditCard,
    "wallet" to Icons.Rounded.AccountBalanceWallet,
    "savings" to Icons.Rounded.Savings,
    "more" to Icons.Rounded.MoreHoriz,
)

private val byKey = CAT_ICONS.toMap()

fun catIcon(key: String?): ImageVector = byKey[key] ?: Icons.Rounded.MoreHoriz

fun accIcon(t: AccType): ImageVector = when (t) {
    AccType.CARD -> Icons.Rounded.CreditCard
    AccType.CASH -> Icons.Rounded.Payments
    AccType.SAVINGS -> Icons.Rounded.Savings
    AccType.OTHER -> Icons.Rounded.AccountBalanceWallet
}
