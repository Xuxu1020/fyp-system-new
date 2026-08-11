# CarMart — Deployment Guide (Render.com)

## Prerequisites
- Your code is pushed to a **GitHub** repository
- You have a **Render.com** account (free at render.com)

---

## Step 1 — Push to GitHub

```bash
git add .
git commit -m "production ready"
git push origin main
```

Make sure your `.gitignore` has:
```
.env
target/
```

---

## Step 2 — Create the Database on Render

1. Go to [render.com/dashboard](https://dashboard.render.com)
2. Click **New** → **PostgreSQL** (free) — *BUT we need MySQL*

> **Render does not offer a free managed MySQL.** Options:
>
> **Option A (Recommended — Free):** Use [PlanetScale](https://planetscale.com) free MySQL tier
> - Sign up → Create database → Get connection string
> - Run `init.sql` via their web console
>
> **Option B:** Use [Railway.app](https://railway.app) — $5 free credit/month, has MySQL
> - New project → Add MySQL → Get connection string
>
> **Option C:** Use Render's PostgreSQL (free) and migrate the app to PostgreSQL
> (requires changing SQL syntax slightly)

### Using PlanetScale (recommended free MySQL)

1. Sign up at planetscale.com
2. Create a database called `carmart_db`
3. Go to **Console** tab and paste the contents of `src/main/webapp/WEB-INF/init.sql`
4. Go to **Connect** → **Connect with** → select **Java / JDBC** → copy the connection string

It will look like:
```
jdbc:mysql://aws.connect.psdb.cloud/carmart_db?sslMode=VERIFY_IDENTITY&...
```

---

## Step 3 — Deploy the Web Service on Render

1. Go to [render.com/dashboard](https://dashboard.render.com)
2. Click **New** → **Web Service**
3. Connect your GitHub repo
4. Configure:

| Setting | Value |
|---------|-------|
| **Name** | `carmart-web` |
| **Runtime** | `Docker` |
| **Instance Type** | `Free` (spins down after 15min inactivity) or `Starter` ($7/mo, always on) |
| **Health Check Path** | `/login.html` |

5. Under **Environment Variables**, add:

| Key | Value |
|-----|-------|
| `DB_URL` | Your JDBC connection string from PlanetScale/Railway |
| `DB_USER` | Your database username |
| `DB_PASSWORD` | Your database password |
| `APP_ENV` | `production` |

6. Under **Disks** (if on paid plan), add:

| Setting | Value |
|---------|-------|
| **Name** | `car-uploads` |
| **Mount Path** | `/uploads` |
| **Size** | `1 GB` |

> Note: The free plan does not support persistent disks. On free tier, uploaded images will be lost on redeploy. Use the Starter plan ($7/mo) for persistence, or integrate cloud storage later.

7. Click **Create Web Service**

Render will:
- Pull your code from GitHub
- Build the Docker image (runs `mvn package`)
- Deploy the Tomcat container
- Give you a URL like: `https://carmart-web.onrender.com`

---

## Step 4 — Test the Deployment

1. Visit `https://carmart-web.onrender.com`
2. Register a user account
3. Log in and add a car listing
4. Check the marketplace

---

## Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_URL` | Yes | Full JDBC URL, e.g. `jdbc:mysql://host:3306/carmart_db?useSSL=true` |
| `DB_USER` | Yes | Database username |
| `DB_PASSWORD` | Yes | Database password |
| `APP_ENV` | Recommended | Set to `production` to enforce strict env var validation |

---

## Updating the App

Push to GitHub → Render automatically rebuilds and redeploys (CI/CD is built in).

```bash
git add .
git commit -m "your change"
git push origin main
```

---

## Costs Summary

| Service | Cost |
|---------|------|
| Render Web Service (free) | $0 (spins down when idle) |
| Render Web Service (starter) | $7/month (always on) |
| PlanetScale MySQL (free) | $0 (5 GB storage) |
| Persistent Disk 1 GB | $0.25/month (only on paid plan) |
| **Total minimum** | **$0** (with cold starts) |
| **Total recommended** | **~$7.25/month** (always on + disk) |
