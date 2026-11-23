/**
 * <b>PROVISIONAL</b> (2026-11-22, v1.2.1): el resumen de una orden en .xlsx, dentro del ZIP de sus informes,
 * pedido por el usuario porque un cliente lo necesita. Una aplicación externa lo hará en el futuro, y
 * entonces este módulo se retira entero.
 *
 * <p><b>Cómo se retira</b>: borrar este paquete y su prueba, y la dependencia {@code fastexcel} del
 * {@code pom.xml}, marcada. Nada más lo nombra: se engancha al ZIP por
 * {@link com.malphasos.malphasos.report.application.ports.output.WorkOrderArchiveExtension}, que el
 * módulo de reportes usa solo si alguien la implementa, y sin implementaciones el ZIP es el de siempre.
 *
 * <p>La receta completa, y el registro de todo lo provisional, en el wiki:
 * {@code SecondBrain/wiki/malphasos/piezas-provisionales.md}.
 *
 * <p>Lee el mismo resumen que el PDF, {@code WorkOrderSummarySheet}, así que dice lo mismo: una fila por
 * equipo atendido, con la marca y el modelo separados y el cierre como fecha, para poder filtrar.
 */
package com.malphasos.malphasos.orderworkbook;
