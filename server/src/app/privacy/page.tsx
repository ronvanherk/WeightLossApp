// Privacy policy for the Google Play listing (closed test for two household members).
export const metadata = { title: "WeightLoss – privacy" };

const section = { marginTop: 28 };

export default function Privacy() {
  return (
    <main style={{ fontFamily: "system-ui, sans-serif", maxWidth: 720, margin: "0 auto", padding: "24px 16px", lineHeight: 1.6 }}>
      <h1>Privacyverklaring WeightLoss</h1>
      <p>Laatst bijgewerkt: 18 oktober 2026</p>

      <p>
        WeightLoss is een privé-app voor het bijhouden van eten, drinken en gewicht. De app is alleen bedoeld voor
        de leden van één huishouden; accounts worden door de beheerder aangemaakt en er is geen openbare registratie.
      </p>

      <h2 style={section}>Welke gegevens</h2>
      <ul>
        <li>Accountgegevens: naam, e-mailadres en een versleuteld wachtwoord.</li>
        <li>Profiel: geslacht, geboortedatum, lengte, activiteitsniveau, streefgewicht en gewenst tempo.</li>
        <li>Gezondheidsgegevens: gewichtsmetingen, wat je eet en drinkt en hoeveel water je drinkt.</li>
        <li>Producten die je zelf aanmaakt en producten die je als favoriet markeert.</li>
      </ul>
      <p>De app gebruikt geen advertenties, geen analyse- of trackingdiensten en vraagt geen locatie of contacten op.</p>

      <h2 style={section}>Waar en waarom</h2>
      <p>
        Alle gegevens staan op een eigen server van de beheerder, beveiligd met HTTPS, en worden alleen gebruikt om
        jouw dagboek, je dagdoel en je voortgang te tonen. Gegevens worden niet verkocht en niet gedeeld met derden.
      </p>
      <p>
        Bij het scannen van een barcode die nog niet bekend is, vraagt de server de productgegevens op bij Open Food
        Facts. Daarbij wordt alleen de barcode verstuurd, geen persoonsgegevens. De camera wordt gebruikt via de
        barcodescanner van Google Play-services; de app krijgt zelf geen toegang tot camerabeelden.
      </p>

      <h2 style={section}>Bewaren en verwijderen</h2>
      <p>
        Gegevens blijven bewaard zolang je account bestaat. Je kunt de beheerder vragen je account en alle bijbehorende
        gegevens te verwijderen; dat gebeurt binnen 30 dagen. Producten die je zelf hebt aangemaakt blijven dan zonder
        jouw naam beschikbaar voor de andere gebruiker.
      </p>

      <h2 style={section}>Contact</h2>
      <p>Ron van Herk, Quantum Vikings — via het e-mailadres van de ontwikkelaar in de Google Play-vermelding.</p>

      <h2 style={section}>Bronnen</h2>
      <p>
        Voedingswaarden: NEVO-online versie 2025/9.0, RIVM, Bilthoven, en Open Food Facts (Open Database License).
      </p>
    </main>
  );
}
