// MDB ProgramDlg.cpp : implementation file
//

#include "stdafx.h"
#include "MDB Program.h"
#include "MDB ProgramDlg.h" 
#include "CMyDialog.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CAboutDlg dialog used for App About

class CAboutDlg : public CDialog
{
public:
	CAboutDlg();

// Dialog Data
	//{{AFX_DATA(CAboutDlg)
	enum { IDD = IDD_ABOUTBOX };
	//}}AFX_DATA

	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CAboutDlg)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	//{{AFX_MSG(CAboutDlg)
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

CAboutDlg::CAboutDlg() : CDialog(CAboutDlg::IDD)
{
	//{{AFX_DATA_INIT(CAboutDlg)
	//}}AFX_DATA_INIT
}

void CAboutDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CAboutDlg)
	//}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CAboutDlg, CDialog)
	//{{AFX_MSG_MAP(CAboutDlg)
		// No message handlers
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CArdacEliteDemoDlg dialog

CArdacEliteDemoDlg::CArdacEliteDemoDlg(CWnd* pParent /*=NULL*/) //有问题
	: CDialog(CArdacEliteDemoDlg::IDD, pParent)
{
	//{{AFX_DATA_INIT(CArdacEliteDemoDlg)
	m_event=0;
	m_nCommsPort = 0;
	m_bNoteHandle = FALSE;
	m_name = 0;
	m_value = 0;
	m_scale = 0;

	//m_db = _T("");
	m_count = _T("");
	//type1 = 0;
	combo1 = -1;
	coinType = 0;

	//}}AFX_DATA_INIT
	// Note that LoadIcon does not require a subsequent DestroyIcon in Win32
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
	
}

void CArdacEliteDemoDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CArdacEliteDemoDlg)
	DDX_Control(pDX, IDC_EDIT, m_cEdit);
	DDX_Control(pDX, IDC_FIRMWARE,m_cFirmware);
	DDX_Text(pDX, IDC_COMMSPORT, m_nCommsPort);
	DDX_Check(pDX, IDC_NOTEHANDLE, m_bNoteHandle);

	DDX_Text(pDX, IDC_NAME, m_name);
	DDX_Text(pDX, IDC_VALUE,m_value);
	DDX_Text(pDX,IDC_COUNT,m_count);
	//}}AFX_DATA_MAP
	DDX_Control(pDX, IDC_COMBO1, type1);
}

BEGIN_MESSAGE_MAP(CArdacEliteDemoDlg, CDialog)
	//{{AFX_MSG_MAP(CArdacEliteDemoDlg)
	ON_WM_SYSCOMMAND()
	ON_WM_PAINT()
	ON_WM_QUERYDRAGICON()
	ON_BN_CLICKED(IDC_OPENPORT, OnOpenport)
	ON_BN_CLICKED(IDC_CLOSEPORT, OnCloseport)
	ON_BN_CLICKED(IDC_NOTEHANDLE, OnNotehandle)
	ON_BN_CLICKED(IDC_SELFTEST, OnSelftest)
	ON_BN_CLICKED(IDC_RESET, OnReset)
	ON_WM_TIMER()
	ON_BN_CLICKED(IDC_ABOUT, OnBnClickedAbout)
	ON_BN_CLICKED(IDC_BUTTON2, OnBnClickedButton2)
	//}}AFX_MSG_MAP

	ON_BN_CLICKED(IDC_BUTTON2, &CArdacEliteDemoDlg::OnBnClickedButton2)
	ON_WM_CTLCOLOR()
	ON_BN_CLICKED(IDC_DISPENSE2, &CArdacEliteDemoDlg::OnBnClickedDispense2)
	ON_COMMAND(ID_32774, &CArdacEliteDemoDlg::OnBnAbout)
	ON_COMMAND(ID_32773, &CArdacEliteDemoDlg::OnMethod)
	ON_COMMAND(ID_32771, &CArdacEliteDemoDlg::OnClear1)
	ON_COMMAND(ID_32772, &CArdacEliteDemoDlg::OnClear2)
	ON_BN_CLICKED(IDC_TUBE, &CArdacEliteDemoDlg::OnBnClickedTube)
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CArdacEliteDemoDlg message handlers

BOOL CArdacEliteDemoDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// Add "About..." menu item to system menu.

	// IDM_ABOUTBOX must be in the system command range.
	ASSERT((IDM_ABOUTBOX & 0xFFF0) == IDM_ABOUTBOX);
	ASSERT(IDM_ABOUTBOX < 0xF000);
	GetDlgItem(IDC_COMBO1)->EnableWindow(false);
	GetDlgItem(IDC_NAME)->EnableWindow(false);
	GetDlgItem(IDC_BUTTON2)->EnableWindow(false);
	GetDlgItem(IDC_VALUE)->EnableWindow(false);
	GetDlgItem(IDC_DISPENSE2)->EnableWindow(false);
	GetDlgItem(IDC_COUNT)->EnableWindow(false);
	GetDlgItem(IDC_TUBE)->EnableWindow(false);;

	CMenu* pSysMenu = GetSystemMenu(false);
	if (pSysMenu != NULL)
	{
		CString strAboutMenu;
		strAboutMenu.LoadString(IDS_ABOUTBOX);
		if (!strAboutMenu.IsEmpty())
		{
			pSysMenu->AppendMenu(MF_SEPARATOR);
			pSysMenu->AppendMenu(MF_STRING, IDM_ABOUTBOX, strAboutMenu);
		}
	}

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	SetIcon(m_hIcon, TRUE);			// Set big icon
	SetIcon(m_hIcon, FALSE);		// Set small icon

	// TODO: Add extra initialization here


	return TRUE;  // return TRUE  unless you set the focus to a control
}

void CArdacEliteDemoDlg::OnSysCommand(UINT nID, LPARAM lParam)
{
	if ((nID & 0xFFF0) == IDM_ABOUTBOX)
	{
		CAboutDlg dlgAbout;
		dlgAbout.DoModal();
	}
	else
	{
		CDialog::OnSysCommand(nID, lParam);
	}
}

// If you add a minimize button to your dialog, you will need the code below
//  to draw the icon.  For MFC applications using the document/view model,
//  this is automatically done for you by the framework.

void CArdacEliteDemoDlg::OnPaint() 
{
	if (IsIconic())
	{
		CPaintDC dc(this); // device context for painting

		SendMessage(WM_ICONERASEBKGND, (WPARAM) dc.GetSafeHdc(), 0);

		// Center icon in client rectangle
		int cxIcon = GetSystemMetrics(SM_CXICON);
		int cyIcon = GetSystemMetrics(SM_CYICON);
		CRect rect;
		GetClientRect(&rect);
		int x = (rect.Width() - cxIcon + 1) / 2;
		int y = (rect.Height() - cyIcon + 1) / 2;

		// Draw the icon
		dc.DrawIcon(x, y, m_hIcon);
	}
	else
	{
		CRect rect;
        CPaintDC dc(this);
        GetClientRect(rect);
        dc.FillSolidRect(rect,RGB(245,245,245)); //设置背景颜色
		CDialog::OnPaint();
	}
}

// The system calls this to obtain the cursor to display while the user drags
//  the minimized window.
HCURSOR CArdacEliteDemoDlg::OnQueryDragIcon()
{
	return (HCURSOR) m_hIcon;
}

void CArdacEliteDemoDlg::OnOpenport() 
{
	// TODO: Add your control notification handler code here

	UpdateData();

	if (!mySerial.Open(m_nCommsPort, 9600))
	{
		m_cEdit.ReplaceSel("Failed to open the serial port！\r\n");
		MessageBox(_T("Please confirm if the serial port number you typed is correct,or the connection status of communcation cable and power cable to changer！"),_T("Warning:"));
	}
	else
	{
		m_event=0;
		GetDlgItem(IDC_OPENPORT)->EnableWindow(false);
    	GetDlgItem(IDC_CLOSEPORT)->EnableWindow(true);
		GetDlgItem(IDC_COMBO1)->EnableWindow(true);
	    GetDlgItem(IDC_NAME)->EnableWindow(true);
	    GetDlgItem(IDC_BUTTON2)->EnableWindow(true);
		GetDlgItem(IDC_VALUE)->EnableWindow(true);
		GetDlgItem(IDC_COUNT)->EnableWindow(true);
		OnReset();
		OnSelftest();


		//DWORD s3 = ::GetTickCount();
		GetDlgItem(IDC_SELFTEST)->EnableWindow(true);
    	GetDlgItem(IDC_RESET)->EnableWindow(true);
    	GetDlgItem(IDC_NOTEHANDLE)->EnableWindow(true);	
		GetDlgItem(IDC_ABOUT)->EnableWindow(true);
		GetDlgItem(IDC_DISPENSE2)->EnableWindow(true);
		GetDlgItem(IDC_TUBE)->EnableWindow(true);

	    UpdateData(FALSE);
	}
}

void CArdacEliteDemoDlg::OnCloseport() 
{
	// TODO: Add your control notification handler code here
		UpdateData();

	if (!mySerial.Close())
	{
		m_cEdit.ReplaceSel("Failed to open the serial port！\r\n");
		m_cEdit.ReplaceSel("  \r\n");
	}
	else
	{
		GetDlgItem(IDC_OPENPORT)->EnableWindow(true);
		GetDlgItem(IDC_CLOSEPORT)->EnableWindow(false);  
		GetDlgItem(IDC_SELFTEST)->EnableWindow(false);
    	GetDlgItem(IDC_RESET)->EnableWindow(false);
    	GetDlgItem(IDC_NOTEHANDLE)->EnableWindow(false);
		GetDlgItem(IDC_ABOUT)->EnableWindow(false);
		GetDlgItem(IDC_DISPENSE2)->EnableWindow(false);
		GetDlgItem(IDC_TUBE)->EnableWindow(false);


		m_cEdit.SetSel(0,-1);
    	m_cEdit.ReplaceSel("");

		UpdateData(false);
	}
}

void CArdacEliteDemoDlg::OnSelftest() 
{
	// TODO: Add your control notification handler code here
	unsigned char buf[32];
	UpdateData();

	CString level, currency1, currency2, factor, decimal, type1, type2, disType1, disType2, tubeType1, tubeType2;
	unsigned char tx[] = { 0x09, 0x09 };  

	mySerial.SendData((const char *)&(tx[0]), 2);
	DWORD t = ::GetTickCount();
	for (;;)
	{
		if ((t + 200) <= ::GetTickCount()) 
		{	
			//mySerial.ReadData((void *)buf, 32); 

			m_cFirmware.ReplaceSel("The configuration of the current module:\r\n");
			m_cFirmware.ReplaceSel("  \r\n");
			int cnt = mySerial.ReadData((void *)buf, 32);

			if (cnt == 24 || cnt == 14) 
			{
				level.Format("%d",buf[0]);
				m_cFirmware.ReplaceSel("Changer Level:" + level + "\r\n");

				currency1.Format("%d",buf[1]);
				currency2.Format("%d",buf[2]);
				m_cFirmware.ReplaceSel("Country/CurrencyCode:" + currency1 + " " + currency2 + "\r\n");//z2,z3 国家电话区号代码

				m_scale = buf[3];
				factor.Format("%d",buf[3]);
				m_cFirmware.ReplaceSel("Caling Factor:" + factor + "\r\n");
				
				decimal.Format("%d",buf[4]);
				m_cFirmware.ReplaceSel("Decimal Places:"+decimal+"\r\n");

				CString n;
				m_cFirmware.ReplaceSel("Coin Type：\r\n");

				if (cnt == 14) //兼容C2 硬币器
				{
					for (int i = 0; i < 6; i++)
					{
						if (buf[7] == 0)
						{
							break;
						}

						double m = 0;

						if (buf[4] < 6) 
						{
							switch (buf[4]) 
							{
							case 0: m = buf[i + 7] * buf[3]; break;
							case 1: m = static_cast<double>(buf[i + 7] * buf[3]) / 10; break;
							case 2: m = static_cast<double>(buf[i + 7] * buf[3]) / 100; break;
							case 3: m = static_cast<double>(buf[i + 7] * buf[3]) / 1000; break;
							case 4: m = static_cast<double>(buf[i + 7] * buf[3]) / 10000; break;
							case 5: m = static_cast<double>(buf[i + 7] * buf[3]) / 100000; break;
							}
						}
						if (buf[4] > 5) 
						{
							m = buf[i + 7] * buf[3];
						}
					
						type1.Format(_T("%.2f"),m);
						n.Format("%d",i+1);
						m_cFirmware.ReplaceSel("Type" + n + ": " + type1 + "\r\n");

						switch (i)
						{
						case 0:denom01 = type1; break;
						case 1:denom02 = type1; break;
						case 2:denom03 = type1; break;
						case 3:denom04 = type1; break;
						case 4:denom05 = type1; break;
						case 5:denom06 = type1; break;
						case 6:denom07 = type1; break;
						case 7:denom08 = type1; break;
						}
					}
				}

				if ( cnt == 24 ) //兼容Gryphoon硬币器
				{
					for (int i = 0; i < 16; i++)
					{
						if (buf[i + 7] == 0)
						{
							break;
						}

						double m = 0;  //小数

						if (buf[4] < 6) 
						{
							switch (buf[4]) 
							{
							case 0: m = buf[i + 7] * buf[3]; break;
							case 1: m = static_cast<double>(buf[i + 7] * buf[3])/10; break;
							case 2: m = static_cast<double>(buf[i + 7] * buf[3])/100; break;
							case 3: m = static_cast<double>(buf[i + 7] * buf[3])/1000; break;
							case 4: m = static_cast<double>(buf[i + 7] * buf[3])/10000; break;
							case 5: m = static_cast<double>(buf[i + 7] * buf[3])/100000; break;
							}
						}

						type1.Format(_T("%.2f"), m);
						n.Format("%d", i + 1);
						m_cFirmware.ReplaceSel("Type" + n + ": " + type1 + "\r\n");

						switch (i)
						{
						case 0:denom01 = type1; break;
						case 1:denom02 = type1; break;
						case 2:denom03 = type1; break;
						case 3:denom04 = type1; break;
						case 4:denom05 = type1; break;
						case 5:denom06 = type1; break;
						case 6:denom07 = type1; break;
						case 7:denom08 = type1; break;
						case 8:denom09 = type1; break;
						case 9:denom10 = type1; break;
						case 10:denom11 = type1; break;
						case 11:denom12 = type1; break;
						case 12:denom13 = type1; break;
						case 13:denom14 = type1; break;
						case 14:denom15 = type1; break;
						case 15:denom16 = type1; break;
						}
					}
				}
				disType1.Format("%d",buf[5]);
				disType2.Format("%d",buf[6]);
				tubeType1 = DecimalToBinary(disType1);//8-15
				tubeType2 = DecimalToBinary(disType2);//0-7

				int z1[8] = { 0 }, z2[8] = { 0 };
				int lenth01, lenth02;
				lenth01 = tubeType1.GetLength();
				lenth02 = tubeType2.GetLength();

				for (int i = 0; i < lenth01; i++)
				{
					z1[i] = tubeType1[lenth01-1 - i] - '0';	//8-15
				}
				for (int i = 0; i < lenth02;i++)
				{
					z2[i] = tubeType2[lenth02 -1 - i] - '0';//0-7
				}
				CString m,x;
				m_cFirmware.ReplaceSel("--------------------------------------------\r\n");
				m_cFirmware.ReplaceSel("Coin Type Routing：\r\n");
				//m_cFirmware.ReplaceSel("  \r\n");
				for (int i = 0; i < 8;i++)
				{
					if (z1[i] == 1) {
						m.Format("%d",i);
						switch (i) 
						{
						case 0:m_cFirmware.ReplaceSel("Type" + m + "：" + denom09 + "\r\n"); break;
						case 1:m_cFirmware.ReplaceSel("Type" + m + "：" + denom10 + "\r\n"); break;
						case 2:m_cFirmware.ReplaceSel("Type" + m + "：" + denom11 + "\r\n"); break;
						case 3:m_cFirmware.ReplaceSel("Type" + m + "：" + denom12 + "\r\n"); break;
						case 4:m_cFirmware.ReplaceSel("Type" + m + "：" + denom13 + "\r\n"); break;
						case 5:m_cFirmware.ReplaceSel("Type" + m + "：" + denom14 + "\r\n"); break;
						case 6:m_cFirmware.ReplaceSel("Type" + m + "：" + denom15 + "\r\n"); break;
						case 7:m_cFirmware.ReplaceSel("Type" + m + "：" + denom16 + "\r\n"); break;
						}
					}
				}
				for (int i = 0; i < 8; i++)
				{
					if (z2[i] == 1) {
						x.Format("%d", i+1);
						switch (i)
						{
						case 0:m_cFirmware.ReplaceSel("Type" + x + "：" + denom01 + "\r\n"); break;
						case 1:m_cFirmware.ReplaceSel("Type" + x + "：" + denom02 + "\r\n"); break;
						case 2:m_cFirmware.ReplaceSel("Type" + x + "：" + denom03 + "\r\n"); break;
						case 3:m_cFirmware.ReplaceSel("Type" + x + "：" + denom04 + "\r\n"); break;
						case 4:m_cFirmware.ReplaceSel("Type" + x + "：" + denom05 + "\r\n"); break;
						case 5:m_cFirmware.ReplaceSel("Type" + x + "：" + denom06 + "\r\n"); break;
						case 6:m_cFirmware.ReplaceSel("Type" + x + "：" + denom07 + "\r\n"); break;
						case 7:m_cFirmware.ReplaceSel("Type" + x + "：" + denom08 + "\r\n"); break;
						}
					}
				}
				m_cFirmware.ReplaceSel("--------------------------------------------\r\n");
				UpdateData(false);
			}
			memset((void *)buf, 0, 32);
			break;
		}
	}
}

void CArdacEliteDemoDlg::OnReset() 
{
	unsigned char buf[5];
	CString str;
	UpdateData();

	unsigned char tx[] = {0x08, 0x08}; //复位
	mySerial.SendData((const char *)&(tx[0]), 2);
	DWORD t = ::GetTickCount();
	for (;;)
	{
		if ((t + 200) <= ::GetTickCount()) 
		{
			//Sleep(200);	
			int cnt = mySerial.ReadData((void *)buf, 5);
			//mySerial.ReadData((void *)buf, 32);
			
			if (buf[0] == 0 && cnt == 1)
			{
                m_cEdit.ReplaceSel("Reset --> OK \r\n");
				m_cEdit.ReplaceSel("--------------------------------------------\r\n");
			}
			memset((void *)buf, 0, 5);
			break;
		}
	}
}

void CArdacEliteDemoDlg::OnNotehandle() 
{// TODO: Add your control notification handler code here

	m_bNoteHandle =! m_bNoteHandle;  
	//OnReset();
	//OnSelftest();
	unsigned char buf[10];
	CString str8,str9;

    if(m_bNoteHandle)
	{
	    unsigned char tx1[] = {0x0C, 0xFF, 0xFF, 0xFF, 0xFF, 0x08};//打开硬币接受类型指令

	    mySerial.SendData((const char *)&(tx1[0]), 6);
		
	    DWORD t1 = ::GetTickCount();
	    for (;;)
	    {
		    if ((t1 + 200) <= ::GetTickCount()) 
		    {
			    int cnt = mySerial.ReadData((void *)buf, 10);
			    if (buf[0] == 0 && cnt == 1)
			    {
					m_cEdit.ReplaceSel("Enable all coin typies successfully\r\n");
			    }
			    memset((void *)buf, 0, 10);
			    break;
		    }
	    }
	    // 启动定时器！同时发送查询事件码、计数的指令。
	     unsigned char tx3[] = {0x0B, 0x0B}; 
	     mySerial.SendData((const char *)&(tx3[0]), 2);
	     GetDlgItem(IDC_NOTEHANDLE)->SetWindowText("End Handling");
	     GetDlgItem(IDC_SELFTEST)->EnableWindow(false);
	     GetDlgItem(IDC_RESET)->EnableWindow(false);
	     GetDlgItem(IDC_ABOUT)->EnableWindow(false);
	     m_cEdit.ReplaceSel(" - - - - - - - -Start inserting！- - - - - - - -\r\n"); 
		 m_cEdit.ReplaceSel("  \r\n");
	     SetTimer(1, 200, NULL);
	}
	else
	{
		//关闭所有通道
		KillTimer(1);
		//Sleep(500);
		unsigned char tx4[] = {0x0C, 0x00, 0x00, 0x00, 0x00, 0x0C}; 
		mySerial.SendData((const char *)&(tx4[0]), 6);
    	DWORD t4 = ::GetTickCount();
    	for (;;)
		{
	    	if ((t4 + 200) <= ::GetTickCount()) 
			{
	    		int cnt = mySerial.ReadData((void *)buf, 10);
	    		if (buf[0] == 0 && cnt == 1)
				{
					m_cEdit.ReplaceSel("Inhibit all coin typies successfully\r\n");
				}
	    		memset((void *)buf, 0, 10);
	    		break;
			}
		}
		GetDlgItem(IDC_NOTEHANDLE)->SetWindowText("Start Handling");
		m_cEdit.ReplaceSel("  \r\n");
		m_cEdit.ReplaceSel("Validating finished,please stop inserting coins!\r\n");
		
		GetDlgItem(IDC_SELFTEST)->EnableWindow(true);
        GetDlgItem(IDC_RESET)->EnableWindow(true);
		GetDlgItem(IDC_ABOUT)->EnableWindow(true);
	}
}

void CArdacEliteDemoDlg::OnTimer(UINT nIDEvent) 
{
	// TODO: Add your message handler code here and/or call default
	UpdateData();
	unsigned char buf[32];
	//CString str;
	unsigned char tx[] = {0x0B, 0x0B};
	unsigned int m_num[16]={0};

	if (nIDEvent == 1)
	{
		int cnt = mySerial.ReadData((void *)buf, 32);
		if (cnt == 3)
		{
			int z1[8],denom,lenth1,lenth2;
			CString str1, str2, str3, str4, type, num, name;

			str1.Format("%d", buf[0]);//进哪里，及币种
			str2.Format("%d", buf[1]);//如果进管子，是累计数量

			str3 = DecimalToBinary(str1);
			str4 = DecimalToBinary(str2);
			lenth1 = str3.GetLength();


			lenth2 = str4.GetLength();

			for (int i = 0; i < lenth1; i++)
			{
				z1[i] = str3[lenth1-1- i] - '0';
			}

			denom = z1[3] * 8 + z1[2] * 4 + z1[1] * 2 + z1[0];

			num.Format("%d", buf[1]);

			if (z1[6] == 1)
			{
				switch (denom) 
				{
				case 0:m_count = denom01; break;
				case 1:m_count = denom02; break;
				case 2:m_count = denom03; break;
				case 3:m_count = denom04; break;
				case 4:m_count = denom05; break;
				case 5:m_count = denom06; break;
				case 6:m_count = denom07; break;
				case 7:m_count = denom08; break;
				case 8:m_count = denom09; break;
				case 9:m_count = denom10; break;
				case 10:m_count = denom11; break;
				case 11:m_count = denom12; break;
				case 12:m_count = denom13; break;
				case 13:m_count = denom14; break;
				case 14:m_count = denom15; break;
				case 15:m_count = denom16; break;
				}
				UpdateData(false);
				if (z1[5] == 0 && z1[4] == 0)
				{
					switch (denom) {
					case 0:m_cEdit.ReplaceSel("cashBox->accepting type 1:" + denom01 + "\r\n"); break;
					case 1:m_cEdit.ReplaceSel("cashBox->accepting type 2:" + denom02 + "分\r\n"); break;
					case 2:m_cEdit.ReplaceSel("cashBox->accepting type 3:" + denom03 + "分\r\n"); break;
					case 3:m_cEdit.ReplaceSel("cashBox->accepting type 4:" + denom04 + "分\r\n"); break;
					case 4:m_cEdit.ReplaceSel("cashBox->accepting type 5:" + denom05 + "分\r\n"); break;
					case 5:m_cEdit.ReplaceSel("cashBox->accepting type 6:" + denom06 + "分\r\n"); break;
					case 6:m_cEdit.ReplaceSel("cashBox->accepting type 7:" + denom07 + "分\r\n"); break;
					case 7:m_cEdit.ReplaceSel("cashBox->accepting type 8:" + denom08 + "分\r\n"); break;
					case 8:m_cEdit.ReplaceSel("cashBox->accepting type 9:" + denom09 + "分\r\n"); break;
					case 9:m_cEdit.ReplaceSel("cashBox->accepting type 10:" + denom10 + "分\r\n"); break;
					case 10:m_cEdit.ReplaceSel("cashBox->accepting type 11:" + denom11 + "分\r\n"); break;
					case 11:m_cEdit.ReplaceSel("cashBox->accepting type 12:" + denom12 + "分\r\n"); break;
					case 12:m_cEdit.ReplaceSel("cashBox->accepting type 13:" + denom13 + "分\r\n"); break;
					case 13:m_cEdit.ReplaceSel("cashBox->accepting type 14:" + denom14 +"分\r\n"); break;
					case 14:m_cEdit.ReplaceSel("cashBox->accepting type 15:" + denom15 +"分\r\n"); break;
					case 15:m_cEdit.ReplaceSel("cashBox->accepting type 16:" + denom16 +"分\r\n"); break;
					}
				}
				if (z1[5] == 0 && z1[4] == 1)
				{

					switch (denom)
					{
					case 0:m_cEdit.ReplaceSel("tube->accepting type 1:" + denom01 + "，count = " + num + "\r\n"); break;
					case 1:m_cEdit.ReplaceSel("tube->accepting type 2:" + denom02 + "，count = " + num + "\r\n"); break;
					case 2:m_cEdit.ReplaceSel("tube->accepting type 3:" + denom03 + "，count = " + num + "\r\n"); break;
					case 3:m_cEdit.ReplaceSel("tube->accepting type 4:" + denom04 + "，count = " + num + "\r\n"); break;
					case 4:m_cEdit.ReplaceSel("tube->accepting type 5:" + denom05 + "，count = " + num + "\r\n"); break;
					case 5:m_cEdit.ReplaceSel("tube->accepting type 6:" + denom06 + "，count = " + num + "\r\n"); break;
					case 6:m_cEdit.ReplaceSel("tube->accepting type 7:" + denom07 + "，count = " + num + "\r\n"); break;
					case 7:m_cEdit.ReplaceSel("tube->accepting type 8:" + denom08 + "，count = " + num + "\r\n"); break;
					case 8:m_cEdit.ReplaceSel("tube->accepting type 9:" + denom09 + "，count = " + num + "\r\n"); break;
					case 9:m_cEdit.ReplaceSel("tube->accepting type 10:" + denom10 + "，count = " + num + "\r\n"); break;
					case 10:m_cEdit.ReplaceSel("tube->accepting type 11:" + denom11 + "，count = " + num + "\r\n"); break;
					case 11:m_cEdit.ReplaceSel("tube->accepting type 12:" + denom12 + "，count = " + num + "\r\n"); break;
					case 12:m_cEdit.ReplaceSel("tube->accepting type 13:" + denom13 + "，count = " + num + "\r\n"); break;
					case 13:m_cEdit.ReplaceSel("tube->accepting type 14:" + denom14 + "，count = " + num + "\r\n"); break;
					case 14:m_cEdit.ReplaceSel("tube->accepting type 15:" + denom15 + "，count = " + num + "\r\n"); break;
					case 15:m_cEdit.ReplaceSel("tube->accepting type 16:" + denom16 + "，count = " + num + "\r\n"); break;
					}
				}
				if (z1[2] == 1 && z1[3] == 1)
				{
					m_cEdit.ReplaceSel("reject coin\r\n");
				}
				m_cEdit.ReplaceSel("--------------------------------------------\r\n");
			}
		}
		mySerial.SendData((const char *)(&tx[0]), 2);
	}
	CDialog::OnTimer(nIDEvent);
}

void CArdacEliteDemoDlg::OnBnClickedAbout()
{
	UpdateData();
	unsigned char buf[8];
	unsigned char tx[] = {0x0F, 0x05, 0x14};//diagnostic status
	mySerial.SendData((const char *)&(tx[0]), 3);
	
	DWORD t = ::GetTickCount();
	for (;;)
	{
		if ((t + 500) <= ::GetTickCount()) 
		{
			//Sleep(200);	
			int cnt = mySerial.ReadData((void *)buf, 18);
	
			if (cnt == 3) 
			{
				m_cEdit.ReplaceSel("error/status: ");
				switch (buf[0]) 
				{
				case 1:m_cEdit.ReplaceSel("01 00 -> powering up\r\n"); break;
				case 2:m_cEdit.ReplaceSel("02 00 -> powering down\r\n"); break;
				case 3:m_cEdit.ReplaceSel("03 00 -> normal\r\n"); break;
				case 4:m_cEdit.ReplaceSel("04 00 -> Keypad shifted\r\n"); break;
				case 5:m_cEdit.ReplaceSel("05 10/20 -> Manual fill/New inventory available\r\n"); break;
				case 6:m_cEdit.ReplaceSel("06 00 -> Inhibited by VMC\r\n"); break;
				case 16:m_cEdit.ReplaceSel("10 E2 -> Changer error\r\n"); break;
				case 17:m_cEdit.ReplaceSel("11 E2 -> Discriminator changer error\r\n"); break;
				case 18:m_cEdit.ReplaceSel("12 E2 -> Accept gate module error\r\n"); break;
				case 19:m_cEdit.ReplaceSel("13 E2 -> Separator module error\r\n"); break;
				case 20:m_cEdit.ReplaceSel("14 E2 -> Dispenser module error\r\n"); break;
				case 21:m_cEdit.ReplaceSel("15 E2 -> Coin tube module error\r\n"); break;
				
				}
				m_cEdit.ReplaceSel("--------------------------------------------\r\n");  
			}
			memset((void *)buf, 0, 16);
			break;
		}
	}	
}


void CArdacEliteDemoDlg::OnBnClickedButton2()
{
	OnSelftest();
	UpdateData(true);
	//Coin Hopper commancation address
	//char c1=m_name.GetAt(0);
	unsigned char buf[32];
	if(m_name <= 0 || m_name > 15 )
	{
		MessageBox(_T("please type the figure between 1 and 15! "),_T("Figure is incorrect:"));
	}
	else if (m_name < 16)
	{
		CString type,amount,type2,amount2;

		int t1[4] = { 0,0,0,0 }, m[4] = {0,0,0,0}, n = m_name;
		amount.Format("%d",n);
		int cointype = type1.GetCurSel(); 
		type.Format("%d",cointype); 
		
		type2 = DecimalToBinary(type);
		amount2 = DecimalToBinary(amount);

		for (int i = 0; i < type2.GetLength();i++) {
			t1[i] = type2[type2.GetLength()-1-i] - '0';
		}
		for (int i = 0; i < amount2.GetLength();i++) {
			m[i] = amount2[amount2.GetLength()-1-i] - '0';
		}

		int byte01 = m[3] * 128 + m[2] * 64 + m[1] * 32 + m[0] * 16 + t1[3] * 8 + t1[2] * 4 + t1[1] * 2 + t1[0];
		
		unsigned char dispense[] = { 0x0D, 0x00, 0x00 }; 
		dispense[1] = byte01;
		dispense[2] = dispense[0] + dispense[1];

		mySerial.SendData((const char *)&(dispense[0]), 3);
		DWORD t = ::GetTickCount();
		for (;;)
		{
			if ((t + 200) <= ::GetTickCount())
			{
				int cnt = mySerial.ReadData((void *)buf, 5);
				if (cnt == 1 && buf[0] == 0)
				{
					m_cEdit.ReplaceSel("Dispense command sent!\r\n");
					m_cEdit.ReplaceSel("--------------------------------------------\r\n");
				}
				memset((void *)buf, 0, 32);
				break;
			}
		}
	}
}
//转换十进制为二进制
CString CArdacEliteDemoDlg::DecimalToBinary(CString strDecimal)
{
	int nDecimal = atoi(strDecimal.GetBuffer(0));

	int nYushu;	//余数
	int nShang;	//商
	CString strBinary = "";
	char buff[2];
	CString str = "";
	BOOL bContinue = TRUE;
	while (bContinue)
	{
		nYushu = nDecimal % 2;
		nShang = nDecimal / 2;
		sprintf(buff, "%d", nYushu);
		str = strBinary;
		strBinary.Format("%s%s", buff, str);
		nDecimal = nShang;
		if (nShang == 0)
			bContinue = FALSE;
	}
	return strBinary;
}


void CArdacEliteDemoDlg::OnBnClickedDispense2()
{
	// TODO: Add your control notification handler code here
	UpdateData(true);
	unsigned char buf[16];
	if (m_value <= 0) {
		MessageBox(_T("The illegal figure！"));
	}
	else if (!isDivisible(m_value,m_scale)) 
	{
		MessageBox(_T("The illegal figure！"));
	}
	else if (isDivisible(m_value, m_scale)) 
	{
		int data = m_value / m_scale;
		//char data2 = (char)(data + 48);

		unsigned char dispense[] = { 0x0F, 0x02, 0x00, 0x00 };
		dispense[2] = data;
		dispense[3] = dispense[0] + dispense[1] + dispense[2];

		mySerial.SendData((const char *)&(dispense[0]), 4);
		DWORD t = ::GetTickCount();
		for (;;)
		{
			if ((t + 200) <= ::GetTickCount())
			{
				int cnt = mySerial.ReadData((void *)buf, 5);
				if (cnt == 1 && buf[0] == 0)
				{
					m_cEdit.ReplaceSel("Dispense command sent!\r\n");
				}
				memset((void *)buf, 0, 16);
				break;
			}
		}
	}

}

CString CArdacEliteDemoDlg::DecToHex(int decimal)
{
	//十进制转十六进制
	CString hexStr;
	char hex[10];
	int num;

	num = _itoa_s(decimal,hex,16);
	hexStr = hex;

	return hexStr;
}

void CArdacEliteDemoDlg::OnBnAbout()
{
	// TODO: Add your command handler code here
	CMyDialog dlg;
	dlg.DoModal();
}


void CArdacEliteDemoDlg::OnMethod()
{
	// TODO: Add your command handler code here
	CString info =
		"   \r\n"
		"The tips how to use this program： \r\n"
		"  \r\n"
		"1.Test inserting: after open serial port,directly hit the button ‘start handling' to start inserting coins;\r\n"
		" \r\n"
		"2.Test payout: there are 2 ways to dispense coins;(1) be spedific one or multiple denoinations and the number you want ,then hit the button ‘Dispense 1’;(2) directly fill the value of coins you want in blank of amount,then hit the button ‘Dispense 2’\r\n"
		" \r\n";
	MessageBox(_T(info));
	
}

void CArdacEliteDemoDlg::OnClear1()
{
	// TODO: Add your command handler code here
	m_cFirmware.SetSel(0, -1);
	m_cFirmware.ReplaceSel("");
	UpdateData(false);
}


void CArdacEliteDemoDlg::OnClear2()
{
	// TODO: Add your command handler code here
	m_cEdit.SetSel(0, -1);
	m_cEdit.ReplaceSel("");
	UpdateData(false);
}

bool CArdacEliteDemoDlg::isDivisible(int a,int b)
{
	return a % b == 0;
}

void CArdacEliteDemoDlg::OnBnClickedTube()
{
	// TODO: Add your control notification handler code here
	OnSelftest();
	unsigned char buf[32];
	UpdateData();

	unsigned char tx[] = { 0x0A, 0x0A }; //TUBE STATUS
	mySerial.SendData((const char *)&(tx[0]), 2);
	DWORD t = ::GetTickCount();
	for (;;)
	{
		if ((t + 200) <= ::GetTickCount())
		{
			//Sleep(200);	
			int cnt = mySerial.ReadData((void *)buf, 32);
			//mySerial.ReadData((void *)buf, 32);

			if (cnt == 19)
			{
				m_cEdit.ReplaceSel("TUBE STATUS --> OK \r\n");
				m_cEdit.ReplaceSel("--------------------------------------------\r\n");

				int z1 = buf[0], z2 = buf[1], z3[16] = { 0 }, z4[16] = { 0 };
				CString full, full2, full3, full4;

				full3.Format("%d",z1);
				full4.Format("%d",z2);

				full = DecimalToBinary(full3); 
				full2 = DecimalToBinary(full4);

			

				for (int i = 0; i < full.GetLength(); i++)
				{
					z3[i] = full[full.GetLength() - 1 - i] - '0';
				}

				for (int i = 0;i< full2.GetLength(); i++)
				{
					z4[i] = full2[full2.GetLength() - 1 - i] - '0';
				}
				for (int i = 0; i < 16; i++)
				{
					CString type11, cs1;
					type11.Format("%d", i + 1);

					if (z3[i] == 1)
					{
						switch (i)
						{
						case 0:cs1 = denom01; break;
						case 1:cs1 = denom02; break;
						case 2:cs1 = denom03; break;
						case 3:cs1 = denom04; break;
						case 4:cs1 = denom05; break;
						case 5:cs1 = denom06; break;
						case 6:cs1 = denom07; break;
						case 8:cs1 = denom08; break;
						}

						m_cEdit.ReplaceSel("The tube of type" + type11 + ":" + cs1 + ",is alreay full\r\n");
					}

					if (z4[i] == 1)
					{
						switch (i)
						{
						case 9:cs1 = denom09; break;
						case 10:cs1 = denom10; break;
						case 11:cs1 = denom11; break;
						case 12:cs1 = denom12; break;
						case 13:cs1 = denom13; break;
						case 14:cs1 = denom14; break;
						case 15:cs1 = denom15; break;
						}
						m_cEdit.ReplaceSel("The tube of type" + type11 + ":" + cs1 + ",is already full\r\n");
					}
				}
				for (int i = 0; i < 16; i++)
				{
					if (buf[i + 2] != 0)
					{
						CString type22, cs2, amount1;
						type22.Format("%d", i + 1);
						amount1.Format("%d", buf[i + 2]);
						switch (i)
						{
						case 0:cs2 = denom01; break;
						case 1:cs2 = denom02; break;
						case 2:cs2 = denom01; break;
						case 3:cs2 = denom02; break;
						case 4:cs2 = denom01; break;
						case 5:cs2 = denom02; break;
						case 6:cs2 = denom01; break;
						case 7:cs2 = denom02; break;
						case 8:cs2 = denom01; break;
						case 9:cs2 = denom02; break;
						case 10:cs2 = denom01; break;
						case 11:cs2 = denom02; break;
						case 12:cs2 = denom01; break;
						case 13:cs2 = denom02; break;
						case 14:cs2 = denom01; break;
						case 15:cs2 = denom02; break;
						}
						m_cEdit.ReplaceSel("Type" + type22 + ":" + cs2 + ",has been accepting " + amount1 + "coins\r\n");
					}
				}
				int sum = 0;
				for (int i = 0; i < 19; i++)
				{
					sum = buf[i]++;
				}
				if (sum == 0) 
				{
					m_cEdit.ReplaceSel("No any coins in changer\r\n");
				
				}
				m_cEdit.ReplaceSel("--------------------------------------------\r\n");
				memset((void *)buf, 0, 32);
				break;
			}
		}
	}
}
