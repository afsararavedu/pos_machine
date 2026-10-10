package com.aifsar.pos.payment.domain;

/** How the card was read at the POS. Chip and contactless carry an EMV cryptogram; swipe does not. */
public enum EntryMode { CHIP, CONTACTLESS, SWIPE }
