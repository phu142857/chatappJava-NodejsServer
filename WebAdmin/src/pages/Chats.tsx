import { useEffect, useState } from 'react';
import { App as AntdApp, Card, Table, Tag, Modal, Typography, Space, Avatar, Button, Form, Input, Select, Tabs } from 'antd';
import { SearchOutlined, DeleteOutlined, MessageOutlined } from '@ant-design/icons';
import apiClient from '../api/client';
import { API_BASE_URL } from '../config';

type ChatItem = {
  _id: string;
  type: 'private' | 'group';
  name?: string;
  lastMessageAt?: string;
  lastActivity?: string;
  lastMessage?: { content?: string };
  participants?: Array<{ 
    user: string | { _id: string; username: string; email?: string; avatar?: string } 
  }>;
};

type Message = {
  _id: string;
  content: string;
  sender?: { _id: string; username: string; avatar?: string };
  chat?: { _id: string; name?: string; type: 'private' | 'group' };
  createdAt: string;
  messageType?: string;
  isDeleted?: boolean;
};

type User = {
  _id: string;
  username: string;
};

export default function Chats() {
  const { message, modal } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<ChatItem[]>([]);
  const [selectedChat, setSelectedChat] = useState<ChatItem | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [allMessages, setAllMessages] = useState<Message[]>([]);
  const [allMessagesLoading, setAllMessagesLoading] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [form] = Form.useForm();

  const resolveAvatarUrl = (avatar?: string) => {
    if (!avatar) return undefined;
    if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar;
    const base = API_BASE_URL.replace(/\/$/, '');
    // Avatar path from server is already in format /uploads/avatars/filename.jpg
    return `${base}${avatar}`;
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get<{ success: boolean; data: { chats: ChatItem[] } }>(
        '/chats/admin'
      );
      console.log('Fetched chats data:', data?.data?.chats);
      setData(data?.data?.chats || []);
    } catch (error) {
      console.error('Failed to fetch chats:', error);
      message.error('Cannot load chats list');
      setData([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchMessages = async (chatId: string) => {
    setMessagesLoading(true);
    try {
      const { data } = await apiClient.get<{ success: boolean; data: { messages: Message[] } }>(
        `/messages/${chatId}`
      );
      setMessages(data?.data?.messages || []);
    } catch (error) {
      console.error('Failed to fetch messages:', error);
      message.error('Cannot load messages');
      setMessages([]);
    } finally {
      setMessagesLoading(false);
    }
  };

  const fetchAllMessages = async (filters?: any) => {
    setAllMessagesLoading(true);
    try {
      const params = new URLSearchParams();
      if (filters?.chatId) params.append('chatId', filters.chatId);
      if (filters?.userId) params.append('userId', filters.userId);
      if (filters?.messageType) params.append('messageType', filters.messageType);
      if (filters?.search) params.append('search', filters.search);

      const { data } = await apiClient.get<{ success: boolean; data: { messages: Message[] } }>(
        `/messages/admin?${params.toString()}`
      );
      setAllMessages(data?.data?.messages || []);
    } catch (error) {
      console.error('Failed to fetch all messages:', error);
      message.error('Cannot load messages list');
      setAllMessages([]);
    } finally {
      setAllMessagesLoading(false);
    }
  };

  const fetchUsers = async () => {
    try {
      const { data } = await apiClient.get<{ success: boolean; data: { users: User[] } }>('/users');
      setUsers(data?.data?.users || []);
    } catch (error) {
      console.error('Failed to fetch users:', error);
    }
  };

  const handleChatClick = (chat: ChatItem) => {
    setSelectedChat(chat);
    fetchMessages(chat._id);
  };

  const handleDeleteMessage = async (messageId: string) => {
    try {
      await apiClient.delete(`/messages/${messageId}`);
      message.success('Message deleted');
      fetchAllMessages(form.getFieldsValue());
      if (selectedChat) {
        fetchMessages(selectedChat._id);
      }
    } catch (error) {
      console.error('Failed to delete message:', error);
      message.error('Cannot delete message');
    }
  };

  const handleDeleteChat = async (chatId: string) => {
    try {
      await apiClient.delete(`/chats/${chatId}/admin`);
      message.success('Chat and all messages deleted');
      fetchData(); // Refresh chat list
      fetchAllMessages(form.getFieldsValue()); // Refresh message list
      if (selectedChat && selectedChat._id === chatId) {
        setSelectedChat(null);
        setMessages([]);
      }
    } catch (error) {
      console.error('Failed to delete chat:', error);
      message.error('Cannot delete chat');
    }
  };

  const onMessageFilterFinish = (values: any) => {
    fetchAllMessages(values);
  };

  const onMessageFilterReset = () => {
    form.resetFields();
    fetchAllMessages();
  };

  useEffect(() => {
    fetchData();
    fetchAllMessages();
    fetchUsers();
  }, []);

  const chatColumns = [
    { title: 'ID', dataIndex: '_id', width: 200 },
    { title: 'Type', dataIndex: 'type', render: (t: string) => <Tag color={t === 'group' ? 'blue' : 'green'}>{t === 'group' ? 'Group' : 'Private'}</Tag> },
    { 
      title: 'Information', 
      render: (_: any, record: ChatItem) => {
        if (record.type === 'private') {
          const participants = record.participants || [];
          if (participants.length === 0) {
            return <Typography.Text type="secondary">No information</Typography.Text>;
          }
          return (
            <div>
              {participants.map((p: any, idx: number) => {
                if (!p || !p.user) return null;
                const user = typeof p.user === 'string' ? { _id: p.user } : p.user;
                if (!user) return null;
                return (
                  <div key={idx} style={{ marginBottom: 4 }}>
                    <Typography.Text strong>UID:</Typography.Text> {user._id || 'N/A'}<br/>
                    <Typography.Text strong>Username:</Typography.Text> {(user as any).username || 'N/A'}
                  </div>
                );
              })}
            </div>
          );
        }
        return record.name || 'Untitled Group';
      }
    },
    { title: 'Members', render: (_: any, r: ChatItem) => (r.participants || []).length },
    { title: 'Last Activity', dataIndex: 'lastActivity', render: (date: string) => date ? new Date(date).toLocaleString() : '-' },
    { title: 'Last Message', render: (_: any, r: ChatItem) => r.lastMessage ? (r.lastMessage.content || 'Media') : '-' },
    {
      title: 'Actions',
      render: (_: any, record: ChatItem) => (
        <Space>
          <Button type="link" onClick={() => handleChatClick(record)}>
            View Messages
          </Button>
          <Button 
            type="text" 
            danger 
            icon={<DeleteOutlined />}
            onClick={() => {
              modal.confirm({
                title: 'Delete Chat',
                content: `Are you sure you want to delete chat "${record.name || 'Chat'}" and all messages? This action cannot be undone.`,
                okText: 'Delete',
                cancelText: 'Cancel',
                okType: 'danger',
                onOk: () => handleDeleteChat(record._id),
              });
            }}
          >
            Delete Chat
          </Button>
        </Space>
      )
    }
  ];

  const messageColumns = [
    { title: 'ID', dataIndex: '_id', width: 200 },
    { 
      title: 'Sender', 
      render: (_: any, record: Message) => (
        <div>
          {record.sender ? (
            <>
              <Typography.Text strong>{record.sender.username}</Typography.Text><br/>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {record.sender._id}
              </Typography.Text>
            </>
          ) : (
            <>
              <Typography.Text strong type="secondary">Deleted User</Typography.Text><br/>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                User does not exist
              </Typography.Text>
            </>
          )}
        </div>
      )
    },
    { 
      title: 'Chat', 
      render: (_: any, record: Message) => (
        <div>
          {record.chat ? (
            <>
              <Tag color={record.chat.type === 'group' ? 'blue' : 'green'}>
                {record.chat.type === 'group' ? 'Group' : 'Private'}
              </Tag><br/>
              <Typography.Text style={{ fontSize: 12 }}>
                {record.chat.name || 'Untitled'}
              </Typography.Text>
            </>
          ) : (
            <>
              <Tag color="red">Chat Deleted</Tag><br/>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Chat does not exist
              </Typography.Text>
            </>
          )}
        </div>
      )
    },
    { 
      title: 'Content', 
      dataIndex: 'content',
      render: (content: string, record: Message) => (
        <div style={{ maxWidth: 300 }}>
          <Typography.Text>{content || 'Media'}</Typography.Text>
          {record.messageType && record.messageType !== 'text' && (
            <Tag style={{ marginLeft: 8 }}>{record.messageType}</Tag>
          )}
        </div>
      )
    },
    { 
      title: 'Time', 
      dataIndex: 'createdAt',
      render: (date: string) => new Date(date).toLocaleString()
    },
    {
      title: 'Actions',
      render: (_: any, record: Message) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={() => {
            modal.confirm({
              title: 'Delete Message',
              content: 'Are you sure you want to delete this message?',
              onOk: () => handleDeleteMessage(record._id),
            });
          }}
        >
          Delete
        </Button>
      )
    }
  ];

  return (
    <Card title="Chat & Message Management">
      <Tabs
        defaultActiveKey="chats"
        items={[
          {
            key: 'chats',
            label: (
              <span>
                <MessageOutlined />
                Chat List
              </span>
            ),
            children: (
              <Table
                rowKey="_id"
                loading={loading}
                dataSource={data}
                pagination={{ pageSize: 20 }}
                columns={chatColumns}
              />
            )
          },
          {
            key: 'messages',
            label: (
              <span>
                <MessageOutlined />
                Message Log
              </span>
            ),
            children: (
              <div>
                <Card size="small" style={{ marginBottom: 16 }}>
                  <Form
                    form={form}
                    layout="inline"
                    onFinish={onMessageFilterFinish}
                    style={{ marginBottom: 16 }}
                  >
                    <Form.Item name="search">
                      <Input placeholder="Search message content" prefix={<SearchOutlined />} />
                    </Form.Item>
                    
                    <Form.Item name="chatId">
                      <Select placeholder="Select chat" style={{ width: 200 }} allowClear>
                        {data.map(chat => (
                          <Select.Option key={chat._id} value={chat._id}>
                            {chat.type === 'private' ? 'Private Chat' : chat.name || 'Untitled Group'}
                          </Select.Option>
                        ))}
                      </Select>
                    </Form.Item>

                    <Form.Item name="userId">
                      <Select placeholder="Select sender" style={{ width: 200 }} allowClear>
                        {users.map(user => (
                          <Select.Option key={user._id} value={user._id}>
                            {user.username}
                          </Select.Option>
                        ))}
                      </Select>
                    </Form.Item>

                    <Form.Item name="messageType">
                      <Select placeholder="Message type" style={{ width: 150 }} allowClear>
                        <Select.Option value="text">Text</Select.Option>
                        <Select.Option value="image">Image</Select.Option>
                        <Select.Option value="file">File</Select.Option>
                        <Select.Option value="audio">Audio</Select.Option>
                        <Select.Option value="video">Video</Select.Option>
                      </Select>
                    </Form.Item>

                    <Form.Item>
                      <Space>
                        <Button type="primary" htmlType="submit">Search</Button>
                        <Button onClick={onMessageFilterReset}>Reset</Button>
                      </Space>
                    </Form.Item>
                  </Form>
                </Card>

                <Table
                  rowKey="_id"
                  loading={allMessagesLoading}
                  dataSource={allMessages}
                  pagination={{ pageSize: 20 }}
                  columns={messageColumns}
                />
              </div>
            )
          }
        ]}
      />
      
      <Modal
        title={`Messages - ${selectedChat?.type === 'private' ? 'Private Chat' : selectedChat?.name || 'Group'}`}
        open={!!selectedChat}
        onCancel={() => {
          setSelectedChat(null);
          setMessages([]);
        }}
        footer={null}
        width={800}
        style={{ top: 20 }}
      >
        <div style={{ height: 400, overflowY: 'auto', border: '1px solid #d9d9d9', padding: 16 }}>
          {messagesLoading ? (
            <div style={{ textAlign: 'center', padding: 20 }}>Loading messages...</div>
          ) : messages.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 20, color: '#999' }}>No messages yet</div>
          ) : (
            <Space direction="vertical" style={{ width: '100%' }}>
              {messages.map((msg) => (
                <div key={msg._id} style={{ 
                  display: 'flex', 
                  alignItems: 'flex-start', 
                  marginBottom: 12,
                  padding: 8,
                  backgroundColor: '#f5f5f5',
                  borderRadius: 8
                }}>
                  <Avatar size="small" src={resolveAvatarUrl(msg.sender?.avatar)} style={{ marginRight: 8 }}>
                    {msg.sender?.username?.[0]?.toUpperCase()}
                  </Avatar>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                      <Typography.Text strong>{msg.sender?.username || 'Deleted User'}</Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {new Date(msg.createdAt).toLocaleString()}
                      </Typography.Text>
                    </div>
                    <Typography.Text>{msg.content}</Typography.Text>
                    {msg.messageType && msg.messageType !== 'text' && (
                      <Tag style={{ marginLeft: 8, fontSize: 12 }}>{msg.messageType}</Tag>
                    )}
                  </div>
                </div>
              ))}
            </Space>
          )}
        </div>
      </Modal>
    </Card>
  );
}


