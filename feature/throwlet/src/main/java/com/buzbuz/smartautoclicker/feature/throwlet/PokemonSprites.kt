package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.widget.ImageView

object PokemonSprites {
    fun bind(imageView: ImageView, pokemonName: String?, placeholderIcon: Int, paddingDp: Int = 10) {
        val context = imageView.context
        if (pokemonName.isNullOrBlank()) {
            showPlaceholder(imageView, placeholderIcon, paddingDp)
            return
        }
        val assetPath = PokemonCatalog.get(context).spriteAssetPath(pokemonName)
        val bitmap = assetPath?.let { path ->
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (bitmap == null) {
            showPlaceholder(imageView, R.drawable.ic_overlay_help, paddingDp = 9)
        } else {
            imageView.setImageBitmap(bitmap)
            imageView.clearColorFilter()
            imageView.setPadding(0, 0, 0, 0)
            imageView.alpha = 1f
        }
    }

    private fun showPlaceholder(imageView: ImageView, iconRes: Int, paddingDp: Int) {
        val padding = (paddingDp * imageView.context.resources.displayMetrics.density).toInt()
        imageView.setImageResource(iconRes)
        imageView.setColorFilter(Color.WHITE)
        imageView.setPadding(padding, padding, padding, padding)
        imageView.alpha = 0.95f
    }
}
