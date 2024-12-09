package com.example.mykfirebaserehz

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class MyAdapter(private val context: Context, private val arraylist: java.util.ArrayList<Photo>) : BaseAdapter() {
    private lateinit var txtminombre: TextView
    private lateinit var txtmialias: TextView
    private lateinit var imgimagen: ImageView
    override fun getCount(): Int {
        return arraylist.size
    }
    override fun getItem(position: Int): Any {
        return position
    }
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        var convertView = convertView
        convertView = LayoutInflater.from(context).inflate(R.layout.misfilas, parent, false)
        txtminombre = convertView.findViewById(R.id.txtminombre)
        txtmialias = convertView.findViewById(R.id.txtMyResult)
        imgimagen  = convertView.findViewById(R.id.imgimagen)

        txtminombre.text = arraylist[position].nombre
        txtmialias.text = arraylist[position].isAFace

        Glide.with(context)
            .load(arraylist.get(position).urlImagen)
            .into(imgimagen);
        return convertView
    }
}