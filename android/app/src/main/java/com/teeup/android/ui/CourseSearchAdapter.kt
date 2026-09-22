package com.teeup.android.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.data.TeeUpApiClient

/**
 * Backs an `AutoCompleteTextView` with server-side course search (GET /api/courses?search=)
 * instead of a fixed pre-fetched list, so a course picker scales to however many courses exist
 * rather than only ever showing what happened to be fetched once up front. Every screen that
 * lets someone pick a course (Register, Playing Details, Create/Edit Group, Start a Round)
 * shares this one adapter.
 *
 * `Filter.performFiltering` already runs off the main thread (Android's own contract for
 * `Filterable`), so the blocking `TeeUpApiClient.fetchCourses` call here is safe as-is — no
 * extra `Thread`/`runOnUiThread` needed.
 */
class CourseSearchAdapter(context: Context) : BaseAdapter(), Filterable {
    private val inflater = LayoutInflater.from(context)
    private var results: List<Course> = emptyList()

    override fun getCount() = results.size
    override fun getItem(position: Int): Course = results[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView as? TextView
            ?: inflater.inflate(R.layout.spinner_dropdown_item, parent, false) as TextView
        view.text = results[position].name
        return view
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString().orEmpty()
            val matches = if (query.isBlank()) emptyList() else try {
                TeeUpApiClient.fetchCourses(search = query)
            } catch (e: Exception) {
                emptyList()
            }
            return FilterResults().apply { values = matches; count = matches.size }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, filterResults: FilterResults?) {
            results = (filterResults?.values as? List<Course>).orEmpty()
            notifyDataSetChanged()
        }

        /** The dropdown fills the field's text with this on selection — without it, tapping a
         *  suggestion would write the [Course] data class's default toString() into the field. */
        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? Course)?.name ?: ""
    }
}
