# Campus Canteen Pre-Ordering and Token System

A Java website (Spring Boot + Thymeleaf + MongoDB) where students and faculty pre-order canteen food,
choose **Cash** (pay at pickup) or **QR Code** (pay by UPI) at checkout, immediately join the queue with a pickup
token, and collect their food in strict first-in, first-out order. Canteen administrators manage the menu, watch
the queue, record cash, verify QR payments and mark orders as taken.

## 1. What you need

- JDK 21 or newer (you have JDK 26).
- IntelliJ IDEA.
- A MongoDB server. For development this is **MongoDB Community Server on your own PC** (it runs as the Windows
  service "MongoDB", port 27017). MongoDB Atlas still works too (see section 3).
- The `MONGODB_URI` environment variable in your IntelliJ run configuration:
  - local development: `mongodb://localhost:27017/campus_canteen`
  - Atlas: your Atlas connection string (keep it in its own run configuration).

Maven does not need to be installed: the project includes the Maven Wrapper (`mvnw` / `mvnw.cmd`).

## 2. Opening and running the project in IntelliJ

1. Open the folder `C:\Projectsss\Campus Canteen Pre-Ordering and Token System` in IntelliJ.
2. **Important:** the project must be loaded as a *Maven* project, otherwise IntelliJ does not know about
   Spring Boot and the run fails with errors like `package org.springframework... does not exist`.
   Right-click `pom.xml` in the Project panel and choose **Add as Maven Project** (if you only see
   **Maven > Reload project**, it is already loaded). Wait until the progress bar at the bottom finishes
   downloading dependencies. A **Maven** tool window appears on the right, showing `campus-canteen`.
3. Create a **separate run configuration for the local database** (your `MongoDB Atlas` one stays unchanged):
   1. **Run > Edit Configurations...**, select `MongoDB Atlas`, click the **Copy Configuration** button
      (two-sheets icon at the top of the list).
   2. Rename the copy to `Local MongoDB`.
   3. **Main class:** `com.campuscanteen.CampusCanteenApplication`; **Use classpath of module:** `campus-canteen`.
   4. In **Environment variables**, click the list icon, select `MONGODB_URI` and replace its value (only in the
      copy) with `mongodb://localhost:27017/campus_canteen`. Click **OK**.
   5. Check: open `MongoDB Atlas` again; it must still have its original value.
4. Choose `Local MongoDB` in the run-configuration drop-down and run it. When the log shows
   `Connected to MongoDB. Using database 'campus_canteen'.` and `Database setup complete.`,
   open <http://localhost:8080>.

The local database and Atlas are completely separate: data saved locally does not appear in Atlas, and the other way round.

Optional settings (environment variables; you do not need any of them):

| Variable | Default | Purpose |
|---|---|---|
| `MONGODB_DATABASE` | `campus_canteen` | Database name (this setting wins over a name written in `MONGODB_URI`) |
| `CANTEEN_TIMEZONE` | `Asia/Kolkata` | Decides "today", the open meal session and token resets |
| `CANTEEN_UPLOAD_DIR` | `uploads` | Folder for payment screenshots and the QR image |
| `PORT` | `8080` | Web server port |

## 3. The database: local MongoDB and Atlas

**If the application cannot connect**, it stops and the log line starting with `Could not connect to MongoDB`
says in plain English what is wrong and what to check (the connection string is never printed):

| Message says | Meaning | Where to fix it |
|---|---|---|
| "No MongoDB server answered" with a `localhost` URI | The local MongoDB service is not running | Windows **Services** app > **MongoDB Server (MongoDB)** > **Start** |
| "Atlas refused the secure connection ... IP address is not on the Atlas allow list" | Atlas does not accept this computer | Atlas > **Security > Network Access** > add your public IP (find it at <https://api.ipify.org>), wait for *Active* |
| "rejected the database username or password" | Wrong user or password in `MONGODB_URI` | Atlas > **Security > Database Access** |
| "cluster address ... could not be found" | Typo in the host part, or no internet/DNS | Copy the string again from Atlas > **Connect > Drivers** |
| "No MongoDB server answered" | Network blocked or offline | Internet connection, firewall/college network (port 27017) |

Network Access checklist when the IP refusal will not go away: the entry is in the **same Atlas project** as the
cluster; it shows **Active**, not *Pending*; the address is the one this PC really uses (a browser VPN/proxy can
make "Add Current IP Address" add a different one; type it by hand); the cluster is not **Paused**.
A college or mobile network may change your public IP from day to day; then add the new one.

If the database stops answering *while* the website is running, pages show "Database unavailable" and login
says the database cannot be reached, so nothing pretends to have worked.

**Collections and indexes** are created automatically at startup by `DatabaseInitializer`. It only creates what is
missing and never deletes data. To see them in **MongoDB Compass**: connect to `mongodb://localhost:27017`, click
the refresh icon next to *Databases*, then open `campus_canteen` (for Atlas: **Database > Browse Collections**).
MongoDB only creates a database when something is first written, so `campus_canteen` appears after the first start.
You should see: `users`, `canteens`, `menuItems`, `mealSessions`, `dailyMenus`, `orders`, `payments`, `tokenCounters`.
Open a collection and click the **Indexes** tab to see its indexes (for example `collegeId_unique` on `users`).

The automated tests use a separate database called `campus_canteen_test` and never touch `campus_canteen`.

## 4. Administrator accounts

There is no default administrator or password. Students and faculty can never choose the administrator role on the
normal registration page.

**Registering at <http://localhost:8080/admin/register>:**

- **First administrator.** While the database has no administrator at all, this page lets one person register as the
  first administrator (the server claims this place with one atomic update, so two people cannot both get it).
  If you set the optional environment variable `CANTEEN_SETUP_CODE`, that code must be typed too, which is a good idea
  if the site can be reached by others before you register.
- **Every later administrator** needs a one-time **invitation code**. An existing administrator opens **Accounts** and
  clicks **Create invitation code**. The code is shown once, works once and expires after 24 hours. Only a hash of it is
  stored (inside the `canteens` document).

Then log in at <http://localhost:8080/admin/login>.

**Alternative: environment variables** (the older method, still supported):

1. In your IntelliJ run configuration, add three environment variables next to `MONGODB_URI`:
   - `CANTEEN_ADMIN_ID` - the admin login ID, e.g. `CANTEEN01`
   - `CANTEEN_ADMIN_NAME` - e.g. `Canteen Manager`
   - `CANTEEN_ADMIN_PASSWORD` - at least 8 characters
2. Run the application once. The log shows `Administrator account CANTEEN01 created.`
3. **Remove the three variables** from the run configuration.
4. Log in at <http://localhost:8080/admin/login>.

An existing account is never changed, and passwords are never reset on restart.

### Forgotten administrator password

There is no "forgot password" page on the website (on purpose). Instead, a small separate program on your
computer sets a new password for an **existing** administrator. It only works with the local MongoDB server
(`localhost`) and only with the databases `campus_canteen` or `campus_canteen_test`. It refuses Atlas and every
other server, never creates or deletes accounts, and only changes the password (stored as a BCrypt hash) plus
the date it was changed.

1. Build the application once (this also runs the tests): in PowerShell in the project folder,
   `.\mvnw.cmd -DskipTests package`
2. In the **same** PowerShell window, set the database address for this window only:
   `$env:MONGODB_URI = 'mongodb://localhost:27017/campus_canteen'`
3. Run: `.\reset-admin-password.cmd --admin-id CANTEENADMIN01 --database campus_canteen`
4. Type the new password twice (8-64 characters; nothing is shown while you type), then type `RESET`.
5. Close the PowerShell window (this removes the temporary `MONGODB_URI` setting) and log in at
   <http://localhost:8080/admin/login>. The website does not need a restart.

The program's source is `src/main/java/com/campuscanteen/tools/AdminPasswordReset.java`.

### Local-development invitation code

For local development you can set a reusable invitation code instead of asking an administrator for a one-time code.
Add the environment variable `CANTEEN_ADMIN_INVITATION_CODE` (for example `YIC@1234`) to the **Local MongoDB** run
configuration, restart, and type that code on <http://localhost:8080/admin/register>. It can be used for any number
of new administrators. The one-time invitation codes keep working as before.

It is **off** unless the variable is set, and even then it is only accepted when MongoDB runs on this computer
(`localhost`) **and** the registration page is opened on this computer. With Atlas, or from another device, it is
refused. Never set it on a deployed server. The code is not stored in the database or written to the log.

## 5. Setting up the canteen (first time, as administrator)

On the first start the application adds, **once only**:

- three meal sessions: **Breakfast** (opening time - 12:00, Breakfast / Tiffins items), **Lunch** (12:00 - 15:00,
  Lunch and Chinese items) and **Snacks** (15:00 - closing time, Snacks and Chinese items);
- 60 **draft food items** copied from the canteen's printed menu: Breakfast / Tiffins, Lunch, Snacks and Chinese
  (Rice, Noodles, Starters). The poster has no prices, so drafts have **no price and no quantity**. Four names that were
  hard to read (Bonda, Karam Dosa, Punugulu, Ajwain Pakoda) are marked **Name needs confirmation** and cannot be sold
  until you check them.

Restarting never adds them again and never changes your prices, timings or edits (a flag on the canteen record remembers
that it was done; a draft you delete stays deleted).

1. **Settings**: upload the canteen's UPI **payment QR code** (JPEG/PNG from your UPI or bank merchant app) and/or
   enter the UPI ID. Until one of them is set, customers can only choose Cash. No payment details are built into
   the code. Also set the opening and closing time: nobody can order outside them.
2. **Food items**: correct names, untick **Name needs confirmation**, change categories, add missing items, set a usual
   price if you like, and **Delete** unwanted drafts (only possible for items never used on a menu or in an order;
   otherwise **Deactivate**). Use the *Drafts only* filter to see what still needs attention.
   **Photos:** 48 of the 60 starter items automatically show a real photo of that dish (bundled in
   `static/images/food`, from Wikimedia Commons; authors and licences on `/images/food/credits.html`, linked in the footer).
   Items without a matching photo show "No photo" rather than a photo of a different dish. On **Edit**, you can choose another
   bundled photo, upload your own (JPEG/PNG, max 5 MB, saved in `uploads/menu-photos`) or paste a link; only the photo's
   address is stored in MongoDB (`imageUrl`).
3. **Meal timings** (optional): change the times or the food categories of each session. A session's *type* decides
   the Chinese rule: Chinese can only be ticked for Lunch and Snacks sessions.
4. **Daily menu**: choose a date and session. Only items of that session's categories are listed. Tick the items to
   sell, enter the **price (more than ₹0)** and **quantity** for each, and click **Save and publish**. The server
   refuses missing prices, unconfirmed names and items the session does not allow (for example Chinese at breakfast).

### How the automatic menu works

The current time in India (`Asia/Kolkata`) picks the session. A session opens exactly at its start time and closes exactly
at its end time, and the canteen's opening hours must also include the time:

| Time | Customers see |
|---|---|
| Opening time until 11:59 | the published **Breakfast** menu (breakfast items only, never Chinese) |
| 12:00 until 14:59 | the published **Lunch** menu (Lunch and Chinese items) |
| 15:00 until closing time | the published **Snacks** menu (Snacks and Chinese items) |
| Outside opening hours | "The canteen is closed now" |

If the session's menu is not published, customers see "The menu for this meal session has not been published yet."
If a customer's cart was filled in one session and the next session starts before checkout, the cart is checked again:
items not on the new menu are removed, the customer is told which ones and why, and prices are recalculated by the server.

## 6. How the parts work together

- **Registration and login.** Students and faculty register with their college ID (stored in upper case, unique).
  Passwords are stored as BCrypt hashes. Spring Security checks the role on every request: `/admin/**` needs
  `CANTEEN_ADMIN`; ordering pages need `STUDENT` or `FACULTY`. College IDs are checked for format and uniqueness only;
  they are **not** verified against an official college roster.
- **Menu.** The open meal session is worked out from the configured timings, the canteen's opening hours and the
  current time. Customers see only the published daily menu for today and that session, and only items whose category
  the session allows (`MealSession.allows`). If no session is open, they see the ordering hours instead.
- **Cart and checkout.** The cart lives in the login session. The checkout page shows the order, the
  **total calculated on the server**, and two buttons beneath it: **Pay by cash** and **Pay by QR code**.
- **Placing the order (both methods).** Pressing either button immediately: re-checks the cart against the published
  menu, recalculates the total, reserves stock with an atomic MongoDB update that only succeeds if enough is left (so
  items cannot be oversold), takes the next daily token, and saves the order as `QUEUED` with its `queueEnteredAt`
  time and the chosen `paymentMethod`. The order is in the FIFO queue from that moment, whatever its payment status.
  Each checkout page carries a random `checkoutKey`; a unique index on (user, checkoutKey) means a double click,
  refresh or back-button resubmit returns the same order instead of creating a second one.
- **Payment status** is kept separate from the order status:
  - Cash: `CASH_DUE` until an administrator clicks **Cash received** (`CASH_COLLECTED`).
  - QR: `QR_PAYMENT_PENDING`; the customer is taken to the QR page (QR code, UPI ID, exact amount, same token) and can
    upload a screenshot (`QR_PROOF_SUBMITTED`). Only an administrator's **Verify** makes it `VERIFIED`, after checking
    the canteen's UPI account. **Reject** marks it `REJECTED`; the order leaves the queue (kept in history with its
    token), and stock is returned if cooking had not started.
- **Tokens.** The next token comes from the `tokenCounters` document for that canteen and day,
  using `findAndModify` with `$inc` (atomic, so no duplicates). A new day uses a new counter, so tokens restart at 001.
  A unique index on orders (canteen + day + token) is an extra safety net.
- **FIFO queue.** There is no separate queue collection. The queue is every order in `QUEUED`, `PREPARING` or `READY`,
  sorted by `queueEnteredAt`, then token, then id. Role and payment status are never used for sorting, so students
  and faculty are equal. The Queue page shows token, customer, items, total, payment method, payment status, order
  status and time, with the action buttons.
- **Order taken.** The administrator moves an order `QUEUED > PREPARING > READY` and clicks **Order taken** when the
  customer collects it (for a cash order: **Cash received & order taken**). It is only allowed once the payment has been
  received. The order becomes `COMPLETED` (time and administrator recorded), leaves the queue automatically and stays in
  MongoDB for history and reports. Each change is recorded in the order's `statusHistory`.
- **No transactions needed.** The local MongoDB server is a single server, not a replica set, so multi-document
  transactions are not available. Every step is one atomic document update; if a later step fails, earlier ones are
  undone (stock returned, the half-saved order marked cancelled).
- **Accounts.** On the **Accounts** page an administrator can disable a student or faculty account (for example one
  registered with someone else's roll number). A disabled account cannot log in or place orders; its orders are kept.
  Administrator accounts are not listed there and cannot be disabled from the website.
- **Uploads.** Images must be JPEG or PNG and at most 5 MB. The browser checks the size before uploading, and the
  server checks it again (`web/UploadSizeFilter.java` turns a too-large upload into the normal "too large" message
  instead of a "Forbidden" error).

## 7. Main files

| File | Purpose |
|---|---|
| `pom.xml` | Maven build: Spring Boot, Thymeleaf, MongoDB, Security, Validation |
| `src/main/resources/application.properties` | Settings (reads `MONGODB_URI`, database name, upload limits) |
| `config/MongoConfig.java` | Connects to MongoDB using `MONGODB_URI` (trims accidental spaces, never logs it) |
| `config/DatabaseInitializer.java` | Pings MongoDB, creates collections and indexes, creates the default canteen |
| `config/SecurityConfig.java` | Login, logout, role rules, BCrypt, CSRF |
| `config/AdminAccountSetup.java` | Creates the first administrator from environment variables |
| `service/AdminRegistrationService.java` | `/admin/register`: first-administrator setup and invitation codes |
| `web/AdminRegistrationController.java` | Administrator registration page and invitation button |
| `config/MongoConnectionProblem.java` | Explains connection failures in plain English |
| `config/StarterMenuSetup.java` | One-time default meal sessions and draft food list from the printed menu |
| `model/MealType.java` | Breakfast / Lunch / Snacks / Other; decides where Chinese food is allowed |
| `web/AdminAccountController.java` | Accounts page (disable / enable student and faculty accounts) |
| `web/UploadSizeFilter.java` | Friendly message for uploads larger than 5 MB |
| `model/*.java` | One class per MongoDB collection (User, Canteen, MenuItem, MealSession, DailyMenu, Order, Payment, TokenCounter) |
| `repository/*.java` | Database access interfaces |
| `service/UserService.java` | Registration and admin creation |
| `service/MealSessionService.java` | Meal timings and "which session is open now" |
| `service/MenuItemService.java`, `DailyMenuService.java` | Catalogue, daily menus, atomic stock reservation |
| `service/CartView.java` | Server-side price and total calculation |
| `service/OrderService.java` | Placing orders (queued at once, duplicate-proof), QR screenshot upload, reading orders |
| `service/PaymentService.java` | Record cash, verify / reject QR payments |
| `service/TokenService.java` | Atomic daily token counter |
| `service/QueueService.java` | FIFO queue, status changes, Order taken, cancellation |
| `service/ReportService.java` | Daily report |
| `service/FileStorageService.java` | Safe image upload (type check, 5 MB limit, random names) |
| `service/FoodPhotos.java` | Bundled food photos and which photo belongs to which food name |
| `web/*Controller.java` | Page handlers for public, customer and admin pages |
| `templates/**/*.html`, `static/css/style.css` | Pages and the single stylesheet |
| `src/test/java/...` | Automated tests |

## 8. Running the tests

In IntelliJ: right-click `src/test/java` > **Run 'All Tests'** (add `MONGODB_URI` to that test run configuration
for the database tests). From PowerShell in the project folder, against the local server:

```
$env:MONGODB_URI = "mongodb://localhost:27017/campus_canteen"
.\mvnw.cmd test
```

Database tests are skipped automatically if `MONGODB_URI` is not set. They only ever use the separate
`campus_canteen_test` database (even though the URI above names `campus_canteen`): every test first checks that the
database name ends in `_test` and refuses to run otherwise. Tests empty `campus_canteen_test` before each test.
If the server refuses the connection, the tests fail at startup with the "Could not connect to MongoDB" explanation;
that is a connection problem, not a bug in the application.

## 9. Known limitations

- QR payments are verified manually by an administrator; there is no payment gateway.
- A customer can have at most 3 unpaid orders in the queue at once (to stop fake cash orders blocking the queue).
- College IDs are not checked against an official roster.
- One canteen is supported in the interface (the data model already stores a canteen ID everywhere).
- Meal sessions cannot cross midnight.
- The food categories are fixed (Breakfast / Tiffins, Lunch, Snacks, Chinese with Rice / Noodles / Starters); adding a
  new category needs a small code change in `MenuItem.CATEGORIES`.
- If you change the opening or closing time in Settings, also adjust the Breakfast start or Snacks end time.
- Uploaded images (payment screenshots, the QR code, uploaded food photos) are stored in the local `uploads` folder;
  back it up together with the database.
- 12 starter items (for example Veg 65, Paneer Noodles, Cut Mirchi) have no bundled photo; upload one on **Food items > Edit**.
