# Geração do catálogo de efeitos

`app/src/main/assets/effects.json` foi gerado a partir do Data List oficial do Yamaha MX49/MX61/MX88
(seções "Effect Type List", "Effect Parameter List" e "Effect Data Assign Table").
Os scripts `parse.py`, `tables.py` e `build.py` (Python + pdfplumber + pdftotext) mostram como: eles esperam o
PDF em `dl.pdf` e os recortes de texto em `L<pág>.txt` / `R<pág>.txt` (`pdftotext -layout` por metade de página).
