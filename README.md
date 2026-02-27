<h1>zpe.lib.sort</h1>

<p>
  This is the official Sorting plugin for ZPE.
</p>

<p>
  The plugin provides flexible and stable sorting functions for lists and maps,
  including numeric sorting, string sorting, natural sorting, and sorting by key.
</p>

<h2>Installation</h2>

<p>
  Place <strong>zpe.lib.sort.jar</strong> in your ZPE native-plugins folder and restart ZPE.
</p>

<p>
  You can also download with the ZULE Package Manager by using:
</p>

<p>
  <code>zpe --zule install zpe.lib.sort.jar</code>
</p>

<h2>Documentation</h2>

<p>
  Full documentation, examples and API reference are available here:
</p>

<p>
  <a href="https://www.jamiebalfour.scot/projects/zpe/documentation/plugins/zpe.lib.sort/" target="_blank">
    View the complete documentation
  </a>
</p>

<h2>Example</h2>

<pre>

import "zpe.lib.sort"

numbers = [5, 2, 10, 1]
print(sort(numbers))

names = ["ZPE", "apple", "Banana"]
print(sort(names, "string_ci"))

rows = [
  [=>]{"name":"Jamie","age":34},
  [=>]{"name":"Alice","age":12},
  [=>]{"name":"Bob","age":12}
]

sorted = sort_by(rows, "age", "number")
print(sorted)

</pre>

<h2>Available Functions</h2>

<ul>
  <li><strong>sort</strong> – Sort a list.</li>
  <li><strong>sort_by</strong> – Sort a list of maps by key.</li>
  <li><strong>sort_map_keys</strong> – Return sorted map keys.</li>
  <li><strong>sort_map_values</strong> – Return sorted map values.</li>
</ul>

<h2>Sorting Modes</h2>

<ul>
  <li><code>"auto"</code> (default)</li>
  <li><code>"number"</code></li>
  <li><code>"string"</code></li>
  <li><code>"string_ci"</code></li>
  <li><code>"natural"</code></li>
</ul>

<h2>Notes</h2>

<ul>
  <li>Sorting is stable.</li>
  <li>Original data structures are not modified.</li>
  <li>Cross-platform (Windows, macOS, Linux).</li>
</ul>