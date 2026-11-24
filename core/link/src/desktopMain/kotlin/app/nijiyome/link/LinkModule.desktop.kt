// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.link

// Desktop: the PC is not a Link client (it is the Link server: module :server), so there is no desktop `linkModule`.
// The shared protocol, client, proxy, pairing and vault (commonMain) compile here and are tested on both targets; the shared
// `linkModule(vault, repository)` is what a desktop graph would call once it has a key store.
// Desktop (by design): sin módulo de Koin de Link en escritorio (sin almacén de clave sellada, descubrimiento mDNS, aviso "Enviar a dispositivo" ni límites del decodificador); escritorio no es cliente de Link.
