package com.example.armineordermodule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Okunan RFID etiketlerinin (EPC) urun barkoduna donusturulmesi.
 *
 * Etiket basim mantigi: her etiketin EPC'si, urunun 13 haneli barkodu + dolgu +
 * o etikete ozgu sira numarasindan olusur (ayni urunun farkli etiketleri farkli
 * EPC'ye sahip olsun diye). Ornek: ayni urunun iki etiketi
 * "868472678515500000000001" ve "868472678515500000000002" -> ikisi de
 * "8684726785155" barkoduna ait, miktar = 2.
 *
 * Bu yuzden gruplama: EPC'nin ilk {@link #BARCODE_LENGTH} hanesini barkod olarak al,
 * ayni barkoda sahip FARKLI (benzersiz) EPC sayisini miktar olarak say. Ayni EPC'nin
 * tekrar okunmasi (RFID okuma teknolojisinin dogasi geregi ayni etiket saniyede
 * onlarca kez okunabilir) miktari ETKILEMEZ.
 *
 * Yalnizca {@value #ACCEPTED_EPC_PREFIX} ile baslayan EPC'ler kabul edilir; digerlari yok sayilir.
 */
public final class EpcBarcodeGrouping {

    private static final int BARCODE_LENGTH = 13;
    /** Manuel / beklenen etiket EPC uzunlugu. */
    public static final int EPC_LENGTH = 24;
    private static final String ACCEPTED_EPC_PREFIX = "868";

    private EpcBarcodeGrouping() {
    }

    /**
     * @param epcIds  benzersiz (dedup edilmis) EPC kimlikleri
     * @return barkod -> miktar (sira eklenme sirasiyla, {@link LinkedHashMap})
     */
    public static Map<String, Integer> groupByBarcode(Collection<String> epcIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (epcIds == null) {
            return counts;
        }
        for (String epc : epcIds) {
            String barcode = extractBarcode(epc);
            if (barcode == null) {
                continue;
            }
            Integer current = counts.get(barcode);
            counts.put(barcode, current == null ? 1 : current + 1);
        }
        return counts;
    }

    public static List<WholesaleOrderLine> toLines(Map<String, Integer> counts) {
        List<WholesaleOrderLine> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            lines.add(new WholesaleOrderLine(entry.getKey(), entry.getValue()));
        }
        return lines;
    }

    /** EPC kabul edilebilir mi (868 ile baslar ve barkod uzunluguna yeter). */
    public static boolean isAcceptedEpc(String epc) {
        return extractBarcode(epc) != null;
    }

    /**
     * Manuel giris formati: tam {@value #EPC_LENGTH} hane ve {@value #ACCEPTED_EPC_PREFIX} ile baslar.
     */
    public static boolean isValidManualEpc(String epc) {
        if (epc == null) {
            return false;
        }
        String value = epc.trim();
        return value.length() == EPC_LENGTH && value.startsWith(ACCEPTED_EPC_PREFIX);
    }

    /**
     * EPC gecersiz/kisa ise veya {@value #ACCEPTED_EPC_PREFIX} ile baslamiyorsa null doner.
     */
    public static String extractBarcode(String epc) {
        if (epc == null || epc.length() < BARCODE_LENGTH) {
            return null;
        }
        if (!epc.startsWith(ACCEPTED_EPC_PREFIX)) {
            return null;
        }
        return epc.substring(0, BARCODE_LENGTH);
    }
}
