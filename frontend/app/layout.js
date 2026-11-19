import './globals.css';

export const metadata = {
  title: 'Pakistan Railways | Book Tickets Online',
  description: 'Official-style booking portal for Pakistan Railways – Dashboard, live fares & seat selection',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1" />
      </head>
      <body>
        <div className="app-shell">
          {children}
        </div>
      </body>
    </html>
  );
}