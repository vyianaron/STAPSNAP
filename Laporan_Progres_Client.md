# Laporan Progres STEPSNAP

**Tanggal:** 8 November 2026  
**Status:** Fase 1–5 telah diimplementasikan; build debug berhasil

## Ringkasan untuk client

STEPSNAP membantu pengguna mencatat perjalanan berjalan, mengabadikan checkpoint, menyimpan tempat menarik, dan melihat pencapaian. Build debug terbaru berhasil.

## Fitur yang tersedia

- Penghitungan langkah harian dan target awal **1.000 langkah**.
- Setelah target tercapai, pengguna dapat menambahkan target berikutnya sebesar 1.000 langkah. Target baru hanya dapat ditambahkan setelah target aktif selesai.
- Notifikasi dan kamera checkpoint tersedia setelah target tercapai.
- Foto checkpoint dapat diberi catatan dan dilihat kembali pada timeline harian.
- Checkpoint yang belum difoto masuk antrean; checkpoint lama di bawah 1.000 langkah tidak membuka kamera.
- POI dapat disimpan dengan nama, kategori, catatan, rating, foto, dan koordinat.
- Peta menampilkan POI, checkpoint yang memiliki lokasi, dan garis rute harian.
- Lima lencana awal mencakup checkpoint, 10.000 langkah sehari, dan streak tujuh hari.
- Statistik profil menampilkan total langkah, jumlah foto, dan streak.

## Catatan

- Target harian dimulai kembali dari 1.000 langkah pada hari berikutnya.
- Peta memerlukan koneksi internet.
- Rute hanya dapat digambar dari checkpoint yang memiliki koordinat; pengisian lokasi otomatis masih perlu divalidasi.
- Build berhasil. Pengujian langsung untuk milestone 1.000 langkah, pembukaan kamera, lokasi, dan lencana masih perlu dilakukan pada perangkat.
