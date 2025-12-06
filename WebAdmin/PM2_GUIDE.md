# Guide PM2 pour WebAdmin

Guide rapide pour exécuter WebAdmin avec PM2.

## 📋 Prérequis

```bash
# Installer PM2 globalement
npm install -g pm2

# Installer serve (pour servir les fichiers statiques en production)
npm install -g serve
```

## 🚀 Mode Production (Recommandé)

### 1. Build l'application

```bash
cd WebAdmin
npm run build
```

Cette commande crée le dossier `dist/` avec les fichiers statiques optimisés.

### 2. Démarrer avec PM2

```bash
# Depuis le dossier WebAdmin
pm2 start ecosystem.config.js --env production
```

### 3. Vérifier le statut

```bash
pm2 status
pm2 logs webadmin
```

L'application sera accessible sur `http://localhost:5173`

## 🔧 Mode Développement avec PM2

Si vous voulez utiliser PM2 pour le mode développement (avec hot-reload):

```bash
pm2 start ecosystem.config.js --only webadmin-dev --env development
```

**Note:** Le mode développement utilise Vite directement, ce qui est moins optimal pour la production mais utile pour le développement.

## 📝 Commandes PM2 Utiles

```bash
# Voir le statut de toutes les applications
pm2 status

# Voir les logs en temps réel
pm2 logs webadmin

# Voir les logs des 100 dernières lignes
pm2 logs webadmin --lines 100

# Redémarrer l'application
pm2 restart webadmin

# Arrêter l'application
pm2 stop webadmin

# Supprimer l'application de PM2
pm2 delete webadmin

# Sauvegarder la configuration actuelle
pm2 save

# Configurer PM2 pour démarrer au boot du système
pm2 startup
# Suivre les instructions affichées
```

## 🔄 Mise à jour de l'application

Quand vous modifiez le code et voulez mettre à jour:

```bash
# 1. Arrêter PM2
pm2 stop webadmin

# 2. Rebuild
npm run build

# 3. Redémarrer
pm2 restart webadmin
```

Ou en une seule commande:
```bash
pm2 stop webadmin && npm run build && pm2 restart webadmin
```

## 📊 Monitoring

```bash
# Dashboard PM2 (interface web)
pm2 web

# Monitoring en temps réel
pm2 monit
```

## 🐛 Dépannage

### L'application ne démarre pas

1. Vérifier que le build a réussi:
   ```bash
   ls -la dist/
   ```

2. Vérifier les logs:
   ```bash
   pm2 logs webadmin --err
   ```

3. Vérifier que le port 5173 n'est pas utilisé:
   ```bash
   lsof -i :5173
   ```

### L'application redémarre en boucle

1. Vérifier les logs d'erreur:
   ```bash
   pm2 logs webadmin --err
   ```

2. Vérifier la mémoire:
   ```bash
   pm2 monit
   ```

### Changer le port

Modifier `ecosystem.config.js`:
```javascript
env: {
  NODE_ENV: 'production',
  PORT: 5174  // Changer ici
}
```

Et dans `package.json`, modifier le script `start`:
```json
"start": "serve -s dist -l 5174"
```

## 📁 Structure des logs

Les logs sont sauvegardés dans:
- `./logs/webadmin-error.log` - Erreurs
- `./logs/webadmin-out.log` - Sortie standard

## ⚙️ Configuration avancée

Le fichier `ecosystem.config.js` contient plusieurs options:

- `max_memory_restart`: Redémarre si la mémoire dépasse 500M
- `autorestart`: Redémarre automatiquement en cas de crash
- `max_restarts`: Nombre maximum de redémarrages avant arrêt
- `restart_delay`: Délai entre les redémarrages

Vous pouvez modifier ces valeurs selon vos besoins.

## 🔗 Liens utiles

- [Documentation PM2](https://pm2.keymetrics.io/docs/usage/quick-start/)
- [Documentation serve](https://github.com/vercel/serve)

