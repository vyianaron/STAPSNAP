# Laporan Pengerjaan Teknis — STEPSNAP

**Tanggal:** 8 November 2026  
**Status:** Fase 1–5 telah diimplementasikan; build debug berhasil

## Ringkasan

STEPSNAP adalah aplikasi pencatat langkah yang menghubungkan aktivitas berjalan dengan checkpoint foto, timeline harian, POI di peta, dan achievement. Data aplikasi disimpan lokal menggunakan DataStore dan Room.

## Fitur utama

- Pelacakan langkah melalui sensor `TYPE_STEP_COUNTER` pada foreground service.
- Target harian dimulai dari **1.000 langkah**. Setelah target tercapai dan checkpoint tercatat, pengguna dapat menambah target sebesar 1.000 langkah.
- Target harian kembali ke 1.000 langkah saat hari berganti.
- Notifikasi checkpoint hanya dibuat saat target aktif tercapai.
- Kamera CameraX dan antrean foto hanya menerima checkpoint yang memenuhi ambang 1.000 langkah. Data checkpoint lama di bawah ambang tersebut tidak lagi masuk antrean kamera.
- Foto checkpoint disimpan di direktori privat aplikasi; catatan foto tersedia di timeline.
- POI dapat ditambah, diedit, dihapus, dan difilter berdasarkan kategori.
- Peta menggunakan MapLibre Native Android 11.8.0 dengan style Liberty dari OpenFreeMap. GeoJSON layer menampilkan POI, checkpoint berkoordinat, lokasi saat ini, dan rute checkpoint.
- Achievement tersimpan unik di Room dan dievaluasi setelah checkpoint.

## Achievement

Lencana awal yang tersedia:

- Checkpoint #1, #10, dan #50.
- 10.000 langkah dalam sehari.
- Streak tujuh hari.

Lencana baru ditampilkan melalui dialog dan status “sudah dilihat” disimpan agar dialog tidak muncul berulang kali.

## Perubahan penyimpanan

- Room versi 4 menambahkan tabel `achievements` dengan migrasi dari versi 3.
- DataStore menyimpan langkah hari ini dan target harian aktif.
- Checkpoint/POI tetap tersimpan di Room.
- Foto CameraX disimpan di penyimpanan privat aplikasi.

## Verifikasi

- `app:assembleDebug` berhasil setelah perubahan target dan kamera.
- Build memverifikasi kompilasi serta packaging; alur milestone, kamera, dan lencana belum diuji secara end-to-end pada perangkat pada sesi ini.

## Batasan yang diketahui

- Total langkah historis dihitung dari nilai checkpoint tertinggi per tanggal; langkah setelah checkpoint terakhir pada hari tersebut dapat belum tercakup.
- Streak dihitung dari tanggal yang memiliki checkpoint tercatat.
- Filter POI tersedia berdasarkan kategori; tag bebas belum dimodelkan.
- Peta memerlukan koneksi internet. Lokasi checkpoint otomatis belum terverifikasi, dan rute hanya memakai checkpoint yang sudah memiliki koordinat.
- Geofencing untuk POI lama belum diimplementasikan.
