package com.dk.himachalfarmdirect.ui.help

/** Help Centre articles (spec D). Plain language, short steps, for first-time smartphone users. */
data class HelpArticle(val id: String, val section: String, val title: String, val body: String)

object HelpContent {
    const val BUYER = "Buyer help"
    const val SELLER = "Seller help"
    const val GENERAL = "General help"

    val articles = listOf(
        // ---------------- BUYER
        HelpArticle("b_login", BUYER, "Registration and login",
            "1. Open the app and tap Continue with Google.\n" +
            "2. Choose your Google account (the one already on your phone). Your account is created automatically.\n" +
            "3. Choose I want to BUY or I am a FARMER / SELLER.\n\n" +
            "No password or OTP is needed. If it says no Google account was found, open phone Settings → Accounts → " +
            "Add account → Google, then try again."),
        HelpArticle("b_nearby", BUYER, "Finding nearby products",
            "1. On Home, tap the location line under the app name.\n" +
            "2. Tap Use my current location, or type your village / PIN code, or move the map pin.\n" +
            "3. Choose a distance: 5, 10, 20 or 50 km.\n" +
            "4. Turn on Home delivery only if you want only farmers who can deliver.\n\n" +
            "Distances are approximate (straight line). A farmer appearing nearby does not always mean they can deliver to you — " +
            "the app confirms delivery at checkout. You can also turn location off in Account and browse all products by category."),
        HelpArticle("b_order", BUYER, "Placing orders",
            "1. Tap Add on a product, or open it and choose the quantity.\n" +
            "2. Open the cart (basket icon on Home).\n" +
            "3. Choose your delivery address and how you want the order: home delivery, collection centre or farm pickup.\n" +
            "4. The app checks with our server that the farmer has stock and serves your area. Green tick = available.\n" +
            "5. Tap Place order. Items from different farmers become separate orders."),
        HelpArticle("b_pay", BUYER, "Payments",
            "Pay the farmer or delivery person by cash or UPI when you receive your order. " +
            "The amount shown at checkout includes the delivery charge. Never share your UPI PIN, OTP or bank details with anyone — " +
            "Himachal Farm Direct staff will never ask for them."),
        HelpArticle("b_track", BUYER, "Delivery tracking",
            "Open the Orders tab. Each order shows its status: Placed → Accepted → Packed → Out for delivery (or Ready for pickup) → Delivered. " +
            "You also get a notification each time the status changes."),
        HelpArticle("b_cancel", BUYER, "Cancellation",
            "You can cancel an order from the Orders tab while it shows Placed (before the farmer accepts it). " +
            "After the farmer accepts, please raise a support request from this Help Centre and mention the order."),
        HelpArticle("b_refund", BUYER, "Returns and refunds",
            "Fresh produce cannot be returned once accepted. If items are spoiled, missing or wrong, raise a complaint within 24 hours " +
            "with the order selected. If you paid by UPI and the complaint is accepted, the refund is sent to the same UPI ID within 5–7 working days."),
        HelpArticle("b_complaint", BUYER, "Complaints",
            "Tap Raise a support request below, choose Complaint, and describe the problem. You get a ticket number straight away. " +
            "Track it under My support tickets — you will get a notification when the administrator replies."),

        // ---------------- SELLER
        HelpArticle("s_register", SELLER, "Farmer registration",
            "1. Sign in with your mobile number.\n" +
            "2. Go to Account → Register as seller.\n" +
            "3. Enter your name, farm name, village, district and PIN.\n" +
            "4. Stand at your farm and tap Use GPS, or drag the map to your farm.\n" +
            "5. Tap Submit for verification."),
        HelpArticle("s_verify", SELLER, "Verification",
            "Our team checks your details, usually within 2 working days, and may call you. " +
            "When you are approved you get a notification and a Buyer/Seller switch appears in Account. " +
            "Until then your products are not shown to buyers."),
        HelpArticle("s_add", SELLER, "Adding products",
            "In Seller mode open the Categories tab (My products) and tap Add product. Enter the name, category, unit, your price and stock. " +
            "Turn on I can deliver this product if you deliver it. Your farm location is attached automatically so nearby buyers can find it."),
        HelpArticle("s_stock", SELLER, "Updating stock",
            "In My products use −1, +1, +10 for quick changes, or open a product to type the exact amount. " +
            "When stock reaches 0 the product is hidden from buyers automatically. Stock also reduces automatically when an order is placed."),
        HelpArticle("s_orders", SELLER, "Managing orders",
            "In Seller mode open the Orders tab. Accept or reject new orders quickly. Then mark them Packed, Out for delivery / Ready for pickup, " +
            "and Delivered. Buyers are notified at every step. Rejecting an order puts the stock back."),
        HelpArticle("s_commission", SELLER, "Commission",
            "The platform commission is a percentage of the item value of delivered orders, set by the administrator and shown to you " +
            "before approval. Delivery charges you collect are not counted for commission."),
        HelpArticle("s_earnings", SELLER, "Earnings",
            "The Seller dashboard shows delivered orders and gross sales. Gross sales minus commission is your earning."),
        HelpArticle("s_settle", SELLER, "Settlements",
            "Settlements for online payments are made weekly to the bank account or UPI you register with the administrator. " +
            "For cash orders you keep the money and the commission is adjusted in the next settlement. " +
            "Raise a support request (topic Settlement) for any mismatch."),

        // ---------------- GENERAL
        HelpArticle("g_location", GENERAL, "Location problems",
            "• \"GPS is off\": tap Turn on in the popup, or switch on Location in your phone's quick settings.\n" +
            "• Permission denied: open Account → Change delivery location → Open app settings, and allow Location.\n" +
            "• Wrong place: drag the map so the pin sits on your house, then Confirm.\n" +
            "• No signal in the hills: search your village or PIN instead — GPS is not required.\n" +
            "• Don't want to share location: choose Browse without location."),
        HelpArticle("g_app", GENERAL, "App problems",
            "• Slow loading: the app saves recent products and images so they open even with weak internet. Pull down or tap Try again.\n" +
            "• App closes: update to the latest version from the person who shared it, then restart your phone.\n" +
            "• Still stuck: raise a support request with topic App problem."),
        HelpArticle("g_security", GENERAL, "Account security",
            "Your login is protected by your Google account. Never share your Google password or any code sent to you. If you lose your phone, contact support to block your account. " +
            "Use Sign out in Account on shared phones."),
        HelpArticle("g_privacy", GENERAL, "Privacy policy",
            "We collect your name, mobile number, delivery addresses and, only when you allow it, your location.\n\n" +
            "• Your exact location and address are never shown publicly. Farmers see only the village/PIN needed to deliver an order you placed with them.\n" +
            "• For new-farmer alerts we store only an approximate area (about 5 km), never your exact point. You can turn this off in Account.\n" +
            "• Farm locations of approved sellers are shown to buyers so they can find produce nearby.\n" +
            "• We do not sell your data. Data is stored on Google Firebase servers.\n" +
            "• To delete your account and data, raise a support request with topic Account."),
        HelpArticle("g_terms", GENERAL, "Terms and conditions",
            "• Himachal Farm Direct connects buyers with local farmers. The farmer is responsible for the quality and quantity of produce.\n" +
            "• Prices are set by farmers. Delivery charges are calculated by the platform from distance and service-area rules.\n" +
            "• Orders are confirmed only after the server checks stock and delivery area.\n" +
            "• Sellers must be verified and must keep stock and prices accurate.\n" +
            "• Misuse, fake orders or abusive behaviour can lead to account suspension."),
    )

    fun section(name: String) = articles.filter { it.section == name }
    fun byId(id: String) = articles.firstOrNull { it.id == id }

    val ticketTopics = listOf(
        "Order problem", "Payment", "Delivery", "Cancellation", "Return / refund", "Complaint",
        "Seller verification", "Commission / earnings", "Settlement", "Location problem", "App problem", "Account", "Other",
    )
}
